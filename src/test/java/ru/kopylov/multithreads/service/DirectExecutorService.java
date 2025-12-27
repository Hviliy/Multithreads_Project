package ru.kopylov.multithreads.service;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public class DirectExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown = false;

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown) {
            throw new IllegalStateException("Executor is shutdown");
        }
        command.run();
    }
}
