package ru.kopylov.multithreads.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.kopylov.multithreads.model.ParseJob;
import ru.kopylov.multithreads.model.ParseStatus;
import ru.kopylov.multithreads.model.Review;
import ru.kopylov.multithreads.service.ReviewService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void postParse_shouldReturnJobId() throws Exception {
        UUID jobId = UUID.randomUUID();

        when(reviewService.startParseAsync("https://example.com/product/123")).thenReturn(jobId);

        mockMvc.perform(post("/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/product/123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));
    }

    @Test
    void getJob_shouldReturnJobStatus() throws Exception {
        UUID jobId = UUID.randomUUID();

        ParseJob job = ParseJob.builder()
                .id(jobId)
                .sourceUrl("https://example.com/product/123")
                .status(ParseStatus.SUCCESS)
                .createdAt(Instant.now())
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .createdReviews(15)
                .errorMessage(null)
                .build();

        when(reviewService.getJob(jobId)).thenReturn(job);

        mockMvc.perform(get("/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.createdReviews").value(15));
    }

    @Test
    void getAnswer_shouldReturnPage() throws Exception {
        Review r = Review.builder()
                .id(1L)
                .sourceUrl("https://example.com/product/123")
                .authorName("user1")
                .rating(5)
                .text("text")
                .createdAt(LocalDate.now().minusDays(2))
                .fetchedAt(Instant.now())
                .build();

        Page<Review> page = new PageImpl<>(
                List.of(r),
                PageRequest.of(0, 20, Sort.by("fetchedAt").descending()),
                1
        );

        when(reviewService.getReviews(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/answer")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "fetchedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].sourceUrl").value("https://example.com/product/123"))
                .andExpect(jsonPath("$.content[0].rating").value(5));
    }
}
