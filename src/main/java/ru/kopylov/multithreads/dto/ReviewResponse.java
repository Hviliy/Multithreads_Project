package ru.kopylov.multithreads.dto;

import ru.kopylov.multithreads.model.Review;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Schema(name = "ReviewResponse", description = "Отзыв, сохранённый в хранилище")
public class ReviewResponse {

    @Schema(description = "ID в базе", example = "1")
    private Long id;

    @Schema(description = "URL источника", example = "https://example.com/product/123")
    private String sourceUrl;

    @Schema(description = "Имя автора", example = "user1234")
    private String authorName;

    @Schema(description = "Рейтинг 1..5", example = "5", minimum = "1", maximum = "5")
    private int rating;

    @Schema(description = "Текст отзыва", example = "Отличный товар! Всё понравилось.")
    private String text;

    @Schema(description = "Дата отзыва на сайте", example = "2025-12-20")
    private LocalDate createdAt;

    @Schema(description = "Дата сохранения", example = "2025-12-27 18:22:11.123")
    private Instant fetchedAt;

    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getSourceUrl(),
                r.getAuthorName(),
                r.getRating(),
                r.getText(),
                r.getCreatedAt(),
                r.getFetchedAt()
        );
    }
}