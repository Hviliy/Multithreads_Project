package ru.kopylov.multithreads.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService parserExecutor() {
        int core = 4;
        int max = 8;
        int queueCapacity = 200;

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueCapacity);

        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("parser-" + n.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };

        return new ThreadPoolExecutor(
                core,
                max,
                60, TimeUnit.SECONDS,
                queue,
                tf,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}