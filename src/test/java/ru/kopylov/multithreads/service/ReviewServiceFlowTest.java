package ru.kopylov.multithreads.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.kopylov.multithreads.controller.SourceStubController;
import ru.kopylov.multithreads.model.ParseStatus;
import ru.kopylov.multithreads.repository.ParseJobRepository;
import ru.kopylov.multithreads.repository.ReviewRepository;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class ReviewServiceFlowTest {

    @TestConfiguration
    static class TestExecutorsConfig {

        @Bean(name = "parserExecutor")
        public ExecutorService parserExecutor() {
            return new DirectExecutorService();
        }

        @Bean(name = "pageExecutor")
        public ExecutorService pageExecutor() {
            return new DirectExecutorService();
        }
    }

    @MockitoBean
    private SourceClient sourceClient;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ParseJobRepository parseJobRepository;

    @BeforeEach
    void cleanDb() {
        reviewRepository.deleteAll();
        parseJobRepository.deleteAll();
    }

    @Test
    void startParseAsync_shouldFinishSuccess_andSave15Reviews() {
        String url = "https://example.com/product/123";

        when(sourceClient.fetchRawReviews(eq(url), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    int count = inv.getArgument(1);
                    int page  = inv.getArgument(2);

                    return java.util.stream.IntStream.range(0, count)
                            .mapToObj(i -> new SourceStubController.RawReviewDto(
                                    "testUser_p" + page + "_" + i,
                                    5,
                                    "test text p=" + page + " i=" + i,
                                    LocalDate.now().minusDays(1)
                            ))
                            .toList();
                });

        UUID jobId = reviewService.startParseAsync(url);

        var job = parseJobRepository.findById(jobId).orElseThrow();

        assertThat(job.getStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(job.getCreatedReviews()).isEqualTo(15);
        assertThat(reviewRepository.count()).isEqualTo(15);
    }
}