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

    private static final int DEFAULT_COUNT = 5;

    private final ReviewRepository reviewRepository;
    private final ParseJobRepository parseJobRepository;
    private final ExecutorService parserExecutor;
    private final ParseJobRunner parseJobRunner;
    private final AsyncEventLogger asyncEventLogger;
    private final ConcurrentHashMap<UUID, Boolean> runningGuards = new ConcurrentHashMap<>();


    public UUID startParseAsync(String url) {
        UUID jobId = UUID.randomUUID();

        asyncEventLogger.logEvent("Создана задача парсинга: id=" + jobId + " url=" + url);

        ParseJob job = ParseJob.builder()
                .id(jobId)
                .sourceUrl(url)
                .status(ParseStatus.QUEUED)
                .createdAt(Instant.now())
                .build();

        parseJobRepository.save(job);
        asyncEventLogger.logEvent("Задача сохранена в БД: id=" + jobId + " status=QUEUED");

        parserExecutor.submit(() -> {
            if (runningGuards.putIfAbsent(jobId, true) != null) {
                asyncEventLogger.logEvent("Повторный запуск задачи предотвращён id=" + jobId);
                return;
            }
            try {
                asyncEventLogger.logEvent("Задача отправлена в пул потоков: id=" + jobId);
                parseJobRunner.run(jobId, url);
            } finally {
                runningGuards.remove(jobId);
                asyncEventLogger.logEvent("защита от дубля: id=" + jobId);
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