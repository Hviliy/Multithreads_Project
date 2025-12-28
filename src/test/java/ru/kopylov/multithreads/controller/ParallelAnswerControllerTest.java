package ru.kopylov.multithreads.controller;

import jakarta.persistence.PrePersist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.kopylov.multithreads.model.Review;
import ru.kopylov.multithreads.repository.ReviewRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ParallelAnswerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();

        Review r1 = Review.builder()
                .sourceUrl("https://example.com/a")
                .authorName("u1")
                .rating(3)
                .text("t1")
                .createdAt(LocalDate.of(2024, 1, 10))
                .fetchedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        Review r2 = Review.builder()
                .sourceUrl("https://example.com/a")
                .authorName("u2")
                .rating(5)
                .text("t2")
                .createdAt(LocalDate.of(2024, 1, 12))
                .fetchedAt(Instant.parse("2025-01-02T00:00:00Z"))
                .build();

        Review r3 = Review.builder()
                .sourceUrl("https://example.com/b")
                .authorName("u3")
                .rating(1)
                .text("t3")
                .createdAt(LocalDate.of(2024, 1, 11))
                .fetchedAt(Instant.parse("2025-01-03T00:00:00Z"))
                .build();

        Review r4 = Review.builder()
                .sourceUrl("https://example.com/b")
                .authorName("u4")
                .rating(4)
                .text("t4")
                .createdAt(LocalDate.of(2024, 1, 9))
                .fetchedAt(Instant.parse("2025-01-04T00:00:00Z"))
                .build();

        reviewRepository.saveAll(List.of(r1, r2, r3, r4));
    }

    @Test
    void parallel_shouldFilterByMinRating() throws Exception {
        mockMvc.perform(get("/answer/parallel")
                        .param("page", "0")
                        .param("size", "20")
                        .param("minRating", "4")
                        .param("sortBy", "rating")
                        .param("direction", "desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].rating", everyItem(greaterThanOrEqualTo(4))));
    }

    @Test
    void parallel_shouldSortByCreatedAtAsc() throws Exception {
        mockMvc.perform(get("/answer/parallel")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "createdAt")
                        .param("direction", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].createdAt").value("2024-01-09"))
                .andExpect(jsonPath("$.content[1].createdAt").value("2024-01-10"))
                .andExpect(jsonPath("$.content[2].createdAt").value("2024-01-11"))
                .andExpect(jsonPath("$.content[3].createdAt").value("2024-01-12"));
    }
}
