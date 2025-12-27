package ru.kopylov.multithreads.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AsyncEventLogger {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventLogger.class);
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(2000);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread worker;

    @PostConstruct
    public void start() {
        worker = new Thread(this::runLoop);
        worker.setName("async-event-logger");
        worker.setDaemon(true);
        worker.start();

        log.info("Логгер запустился (daemon={})", worker.isDaemon());
    }

    public void logEvent(String message) {
        String msg = Instant.now() + " [" + Thread.currentThread().getName() + "] " + message;
        boolean accepted;
        try {
            accepted = queue.offer(msg, 50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            accepted = false;
        }

        if (!accepted) {
            log.warn("Очередь логгера переполнена: {}", msg);
        }
    }

    private void runLoop() {
        try {
            while (running.get() || !queue.isEmpty()) {
                String msg = queue.poll(300, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    log.info("event: {}", msg);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Логгер крашнулся", e);
        } finally {
            log.info("Логгер остановился");
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }
}