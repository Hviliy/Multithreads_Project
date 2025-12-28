package ru.kopylov.multithreads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.kopylov.multithreads.controller.SourceStubController;
import ru.kopylov.multithreads.model.ParseJob;
import ru.kopylov.multithreads.model.ParseStatus;
import ru.kopylov.multithreads.model.Review;
import ru.kopylov.multithreads.repository.ParseJobRepository;
import ru.kopylov.multithreads.repository.ReviewRepository;
import ru.kopylov.multithreads.util.DedupUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;


@Service
@RequiredArgsConstructor
public class ParseJobRunner {
    private static final int LATCH_TIMEOUT_SECONDS = 10;

    private final ParseJobRepository parseJobRepository;
    private final ReviewRepository reviewRepository;
    private final SourceClient sourceClient;
    private final SteamReviewsClient steamReviewsClient;
    private final ExecutorService pageExecutor;
    private final AsyncEventLogger asyncEventLogger;

    @Value("${parser.per-page:5}")
    private int perPage;

    @Value("${parser.pages:3}")
    private int pages;

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

                var collected = new java.util.ArrayList<SourceStubController.RawReviewDto>(pages * perPage);

                String cursor = "*";

                for (int p = 0; p < pages; p++) {
                    asyncEventLogger.logEvent("Страница стим " + p + ": HTTP запрос id=" + jobId);

                    SteamReviewsClient.SteamPage page = steamReviewsClient.fetchPage(url, cursor, perPage);
                    cursor = page.nextCursor();

                    var rawPage = page.reviews();
                    asyncEventLogger.logEvent("Страница стим " + p + ": получено=" + rawPage.size() + ", id=" + jobId);

                    collected.addAll(rawPage);

                    if (rawPage.isEmpty()) {
                        asyncEventLogger.logEvent("Страница стим " + p + ": пусто, останавливаем пагинацию, id=" + jobId);
                        break;
                    }
                }

                if (collected.isEmpty()) {
                    throw new IllegalStateException("не удалось получить ни одного отзыва (проверьте appid/URL)");
                }

                asyncEventLogger.logEvent("Steam: сбор завершён, totalRaw=" + collected.size() + ", id=" + jobId);

                var q = new java.util.concurrent.ConcurrentLinkedQueue<SourceStubController.RawReviewDto>(collected);
                saveReviewsAndCompleteJob(job, jobId, url, q);
                return;
            }
            // стаб отзывы
            asyncEventLogger.logEvent("Источник не Steam. Используем стабы");

            asyncEventLogger.logEvent("Начинаем параллельный сбор страниц: pages=" + pages +
                    ", perPage=" + perPage + ", id=" + jobId);

            CountDownLatch latch = new CountDownLatch(pages);

            ConcurrentLinkedQueue<SourceStubController.RawReviewDto> collected = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

            for (int p = 0; p < pages; p++) {
                final int page = p;

                pageExecutor.submit(() -> {
                    asyncEventLogger.logEvent("Страница " + page + ": старт загрузки, id=" + jobId);
                    try {
                        var rawPage = sourceClient.fetchRawReviews(url, perPage, page);
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
                .map(r -> {
                    String normAuthor = (r.getAuthorName() == null ? "" : r.getAuthorName().trim());
                    String normText = (r.getText() == null ? "" : r.getText().trim());
                    String normDate = (r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());

                    String keyMaterial = normAuthor + "|" + normDate + "|" + r.getRating() + "|" + normText;
                    String dedupKey = DedupUtils.sha256Hex(keyMaterial);

                    return Review.builder()
                            .sourceUrl(url)
                            .authorName(normAuthor.isEmpty() ? "unknown" : normAuthor)
                            .rating(r.getRating())
                            .text(normText)
                            .createdAt(r.getCreatedAt())
                            .fetchedAt(now)
                            .dedupKey(dedupKey)
                            .build();
                })
                .toList();

        var uniqueByKey = new java.util.LinkedHashMap<String, Review>();
        for (Review rev : batch) {
            uniqueByKey.putIfAbsent(rev.getDedupKey(), rev);
        }
        var uniqueBatch = uniqueByKey.values().stream().toList();

        var keys = uniqueBatch.stream().map(Review::getDedupKey).toList();
        var existing = new java.util.HashSet<>(reviewRepository.findExistingDedupKeys(url, keys));

        var toSave = uniqueBatch.stream()
                .filter(r -> !existing.contains(r.getDedupKey()))
                .toList();

        reviewRepository.saveAll(toSave);

        int newSaved = toSave.size();
        int totalUnique = uniqueBatch.size();
        int skipped = totalUnique - newSaved;

        asyncEventLogger.logEvent("Отзывы сохранены в БД: saved=" + newSaved +
                " (из " + totalUnique + "), id=" + jobId);

        job.setStatus(ParseStatus.SUCCESS);
        job.setFinishedAt(Instant.now());
        job.setCreatedReviews(newSaved);
        job.setErrorMessage(null);
        parseJobRepository.save(job);

        asyncEventLogger.logEvent("Задача завершена успешно: id=" + jobId +
                ", новых=" + newSaved + ", пропущено дублей=" + skipped);
    }
}