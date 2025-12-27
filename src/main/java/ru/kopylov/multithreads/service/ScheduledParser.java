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
    private final AsyncEventLogger asyncEventLogger;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void runScheduled() {
        var sources = sourceUrlRepository.findAllByEnabledTrue();
        asyncEventLogger.logEvent("Планировщик: запуск цикла, активных источников=" + sources.size());

        for (var s : sources) {
            var jobId = reviewService.startParseAsync(s.getUrl());
            asyncEventLogger.logEvent("Планировщик: запущена задача парсинга: id=" + jobId + " url=" + s.getUrl());

            s.setLastTriggeredAt(Instant.now());
        }
        sourceUrlRepository.saveAll(sources);
    }
}