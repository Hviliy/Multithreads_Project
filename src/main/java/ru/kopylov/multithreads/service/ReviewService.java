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
    private final SourceClient sourceClient;
    private final ExecutorService parserExecutor;
    private final ParseJobRunner parseJobRunner;
    private final ConcurrentHashMap<UUID, Boolean> runningGuards = new ConcurrentHashMap<>();


    public UUID startParseAsync(String url) {
        UUID jobId = UUID.randomUUID();

        ParseJob job = ParseJob.builder()
                .id(jobId)
                .sourceUrl(url)
                .status(ParseStatus.QUEUED)
                .createdAt(Instant.now())
                .build();

        parseJobRepository.save(job);

        parserExecutor.submit(() -> {
            if (runningGuards.putIfAbsent(jobId, true) != null) return;
            try {
                parseJobRunner.run(jobId, url);
            } finally {
                runningGuards.remove(jobId);
            }
        });

        return jobId;
    }


    @Transactional
    public void runJob(UUID jobId, String url) {
        if (runningGuards.putIfAbsent(jobId, true) != null) {
            return;
        }

        try {
            ParseJob job = parseJobRepository.findById(jobId).orElseThrow();
            job.setStatus(ParseStatus.RUNNING);
            job.setStartedAt(Instant.now());
            parseJobRepository.save(job);

            var raw = sourceClient.fetchRawReviews(url, DEFAULT_COUNT);

            var now = Instant.now();
            var batch = raw.stream().map(r -> Review.builder()
                    .sourceUrl(url)
                    .authorName(r.getAuthorName())
                    .rating(r.getRating())
                    .text(r.getText())
                    .createdAt(r.getCreatedAt())
                    .fetchedAt(now)
                    .build()
            ).toList();

            reviewRepository.saveAll(batch);

            job.setStatus(ParseStatus.SUCCESS);
            job.setFinishedAt(Instant.now());
            job.setCreatedReviews(batch.size());
            parseJobRepository.save(job);

        } catch (Exception e) {
            ParseJob job = parseJobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus(ParseStatus.FAILED);
                job.setFinishedAt(Instant.now());
                job.setErrorMessage(e.toString());
                parseJobRepository.save(job);
            }
        } finally {
            runningGuards.remove(jobId);
        }
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