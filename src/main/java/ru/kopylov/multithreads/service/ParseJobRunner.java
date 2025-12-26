package ru.kopylov.multithreads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.kopylov.multithreads.model.Review;
import ru.kopylov.multithreads.model.ParseStatus;
import ru.kopylov.multithreads.model.ParseJob;
import ru.kopylov.multithreads.repository.ParseJobRepository;
import ru.kopylov.multithreads.repository.ReviewRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParseJobRunner {

    private static final int DEFAULT_COUNT = 5;

    private final ParseJobRepository parseJobRepository;
    private final ReviewRepository reviewRepository;
    private final SourceClient sourceClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(UUID jobId, String url) {
        ParseJob job = parseJobRepository.findById(jobId).orElseThrow();

        job.setStatus(ParseStatus.RUNNING);
        job.setStartedAt(Instant.now());
        parseJobRepository.save(job);

        try {
            var raw = sourceClient.fetchRawReviews(url, DEFAULT_COUNT);
            var now = Instant.now();

            var batch = raw.stream()
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

            job.setStatus(ParseStatus.SUCCESS);
            job.setFinishedAt(Instant.now());
            job.setCreatedReviews(batch.size());
            parseJobRepository.save(job);

        } catch (Exception e) {
            job.setStatus(ParseStatus.FAILED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(e.toString());
            parseJobRepository.save(job);
        }
    }
}
