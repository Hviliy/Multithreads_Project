package ru.kopylov.multithreads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.kopylov.multithreads.controller.SourceStubController;
import ru.kopylov.multithreads.model.ParseJob;
import ru.kopylov.multithreads.model.ParseStatus;
import ru.kopylov.multithreads.model.Review;
import ru.kopylov.multithreads.repository.ParseJobRepository;
import ru.kopylov.multithreads.repository.ReviewRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;


@Service
@RequiredArgsConstructor
public class ParseJobRunner {
    private static final int PER_PAGE = 5;
    private static final int PAGES = 3;
    private static final int LATCH_TIMEOUT_SECONDS = 10;

    private final ParseJobRepository parseJobRepository;
    private final ReviewRepository reviewRepository;
    private final SourceClient sourceClient;
    private final SteamReviewsClient steamReviewsClient;
    private final ExecutorService pageExecutor;
    private final AsyncEventLogger asyncEventLogger;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(UUID jobId, String url) {
        asyncEventLogger.logEvent("Запуск выполнения задачи: id=" + jobId + " url=" + url);

        ParseJob job = parseJobRepository.findById(jobId).orElseThrow();

        job.setStatus(ParseStatus.RUNNING);
        job.setStartedAt(Instant.now());
        parseJobRepository.save(job);

        asyncEventLogger.logEvent("Статус задачи: RUNNING id=" + jobId);

        // стим отзывы
        try {
            if (steamReviewsClient.supports(url)) {
                asyncEventLogger.logEvent("Источник Steam");

                CountDownLatch latch = new CountDownLatch(PAGES);
                ConcurrentLinkedQueue<SourceStubController.RawReviewDto> collected = new ConcurrentLinkedQueue<>();
                ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

                String cursor = "*";

                for (int p = 0; p < PAGES; p++) {
                    final int pageNo = p;

                    asyncEventLogger.logEvent("Страница стим " + pageNo + ": HTTP запрос id=" + jobId);
                    var page = steamReviewsClient.fetchPage(url, cursor, PER_PAGE);
                    cursor = page.nextCursor();

                    var rawPage = page.reviews();

                    pageExecutor.submit(() -> {
                        asyncEventLogger.logEvent("Страница стим " + pageNo + ": обработка старт id=" + jobId);
                        try {
                            collected.addAll(rawPage);
                            asyncEventLogger.logEvent("Страница стим " + pageNo + ": обработка ок, шт=" +
                                    rawPage.size() + ", поток=" + Thread.currentThread().getName());
                        } catch (Exception e) {
                            errors.add(e);
                            asyncEventLogger.logEvent("Страница стим " + pageNo + ": ошибка обработки=" + e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                boolean done = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!done) {
                    throw new TimeoutException("Не дождались обработки страниц за " +
                            LATCH_TIMEOUT_SECONDS + " сек (id=" + jobId + ")");
                }
                if (!errors.isEmpty()) {
                    Throwable first = errors.peek();
                    throw new RuntimeException("ошибка обработки страниц: " + first, first);
                }
                if (collected.isEmpty()) {
                    throw new IllegalStateException("не удалось получить ни одного отзыва (проверьте appid/URL)");
                }

                asyncEventLogger.logEvent("все страницы обработаны, totalRaw=" + collected.size() + ", id=" + jobId);

                saveReviewsAndCompleteJob(job, jobId, url, collected);
                return;
            }
            // стаб отзывы
            asyncEventLogger.logEvent("Источник не Steam. Используем стабы");

            asyncEventLogger.logEvent("Начинаем параллельный сбор страниц: pages=" + PAGES +
                    ", perPage=" + PER_PAGE + ", id=" + jobId);

            CountDownLatch latch = new CountDownLatch(PAGES);

            ConcurrentLinkedQueue<SourceStubController.RawReviewDto> collected = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

            for (int p = 0; p < PAGES; p++) {
                final int page = p;

                pageExecutor.submit(() -> {
                    asyncEventLogger.logEvent("Страница " + page + ": старт загрузки, id=" + jobId);
                    try {
                        var rawPage = sourceClient.fetchRawReviews(url, PER_PAGE, page);
                        collected.addAll(rawPage);

                        asyncEventLogger.logEvent("Страница " + page + ": получено=" + rawPage.size() +
                                ", id=" + jobId + ", поток=" + Thread.currentThread().getName());
                    } catch (Exception e) {
                        errors.add(e);
                        asyncEventLogger.logEvent("Страница " + page + ": ошибка=" + e +
                                ", id=" + jobId + ", поток=" + Thread.currentThread().getName());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean done = latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!done) {
                throw new TimeoutException("Не дождались завершения загрузки страниц за " +
                        LATCH_TIMEOUT_SECONDS + " сек (id=" + jobId + ")");
            }

            if (!errors.isEmpty()) {
                Throwable first = errors.peek();
                throw new RuntimeException("Ошибки при загрузке страниц: " + first, first);
            }
            if (collected.isEmpty()) {
                throw new IllegalStateException("Не удалось получить ни одного отзыва (проверь appid/URL)");
            }

            asyncEventLogger.logEvent("Все страницы собраны: totalRaw=" + collected.size() + ", id=" + jobId);

            saveReviewsAndCompleteJob(job, jobId, url, collected);

        } catch (Exception e) {
            job.setStatus(ParseStatus.FAILED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(e.toString());
            parseJobRepository.save(job);

            asyncEventLogger.logEvent("Ошибка выполнения задачи: id=" + jobId + ", причина=" + e);
        }
    }

    private void saveReviewsAndCompleteJob(ParseJob job,
                                           UUID jobId,
                                           String url,
                                           ConcurrentLinkedQueue<SourceStubController.RawReviewDto> collected) {

        Instant now = Instant.now();
        var batch = collected.stream()
                .map(r -> Review.builder()
                        .sourceUrl(url)
                        .authorName(r.getAuthorName())
                        .rating(r.getRating())
                        .text(r.getText())
                        .createdAt(r.getCreatedAt())
                        .fetchedAt(now)
                        .build())
                .toList();

        reviewRepository.saveAll(batch);

        asyncEventLogger.logEvent("Отзывы сохранены в БД: saved=" + batch.size() + ", id=" + jobId);

        job.setStatus(ParseStatus.SUCCESS);
        job.setFinishedAt(Instant.now());
        job.setCreatedReviews(batch.size());
        parseJobRepository.save(job);

        asyncEventLogger.logEvent("Задача завершена успешно: id=" + jobId + ", createdReviews=" + batch.size());
    }
}