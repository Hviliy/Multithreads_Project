package ru.kopylov.multithreads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kopylov.multithreads.model.ParseJob;
import ru.kopylov.multithreads.model.ParseStatus;
import ru.kopylov.multithreads.model.Review;
import ru.kopylov.multithreads.repository.ParseJobRepository;
import ru.kopylov.multithreads.repository.ReviewRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ParseJobRepository parseJobRepository;
    private final ExecutorService parserExecutor;
    private final ParseJobRunner parseJobRunner;
    private final AsyncEventLogger asyncEventLogger;
    private final ConcurrentHashMap<String, Boolean> runningGuards = new ConcurrentHashMap<>();
    private final SteamReviewsClient steamReviewsClient;


    public UUID startParseAsync(String url) {
        UUID jobId = UUID.randomUUID();

        asyncEventLogger.logEvent("Создана задача парсинга: id=" + jobId + " url=" + url);

        ParseJob job = ParseJob.builder()
                .id(jobId)
                .sourceUrl(url)
                .status(ParseStatus.QUEUED)
                .createdAt(Instant.now())
                .build();

        parseJobRepository.saveAndFlush(job);
        asyncEventLogger.logEvent("Задача сохранена в БД: id=" + jobId + " status=QUEUED");

        String guardKey = url == null ? "" : url.trim();

        if (steamReviewsClient.supports(guardKey)) guardKey = "steam:" + steamReviewsClient.extractAppId(guardKey);
        String finalGuardKey = guardKey;
        parserExecutor.execute(() -> {
            if (runningGuards.putIfAbsent(finalGuardKey, true) != null) {
                asyncEventLogger.logEvent("Повторный запуск источника предотвращён: url="
                        + finalGuardKey + ", jobId=" + jobId);

                ParseJob j = parseJobRepository.findById(jobId).orElseThrow();
                j.setStatus(ParseStatus.SUCCESS);
                j.setFinishedAt(Instant.now());
                j.setCreatedReviews(0);
                j.setErrorMessage("Источник уже в обработке (guard)");
                parseJobRepository.save(j);
                return;
            }

            try {
                asyncEventLogger.logEvent("Задача отправлена в пул потоков: id=" + jobId);


                try {
                    parseJobRunner.run(jobId, url);
                } catch (Throwable t) {
                    asyncEventLogger.logEvent("ошибка вне обработчика run(): jobId=" + jobId + " причина=" + t);

                    ParseJob j = parseJobRepository.findById(jobId).orElse(null);
                    if (j != null) {
                        j.setStatus(ParseStatus.FAILED);
                        j.setFinishedAt(Instant.now());
                        j.setErrorMessage("Ошибка: " + t);
                        parseJobRepository.save(j);
                    } else {
                        asyncEventLogger.logEvent("Не нашёл задачу в БД для обновления статуса: id=" + jobId);
                    }
                }
            } finally {
                runningGuards.remove(finalGuardKey);
                asyncEventLogger.logEvent("guard освобождён: url=" + finalGuardKey + ", jobId=" + jobId);
            }
        });

        return jobId;
    }

    @Transactional(readOnly = true)
    public Page<Review> getReviews(Pageable pageable) {
        return reviewRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ParseJob getJob(UUID id) {
        return parseJobRepository.findById(id).orElseThrow();
    }
}