package ru.kopylov.multithreads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.kopylov.multithreads.repository.SourceUrlRepository;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ScheduledParser {

    private final SourceUrlRepository sourceUrlRepository;
    private final ReviewService reviewService;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void runScheduled() {
        var sources = sourceUrlRepository.findAllByEnabledTrue();
        for (var s : sources) {
            reviewService.startParseAsync(s.getUrl());
            s.setLastTriggeredAt(Instant.now());
        }
        sourceUrlRepository.saveAll(sources);
    }
}