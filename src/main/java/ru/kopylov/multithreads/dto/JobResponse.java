package ru.kopylov.multithreads.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.kopylov.multithreads.model.ParseJob;
import ru.kopylov.multithreads.model.ParseStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class JobResponse {
    private UUID id;
    private String sourceUrl;
    private ParseStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer createdReviews;
    private String errorMessage;

    public static JobResponse from(ParseJob j) {
        return new JobResponse(
                j.getId(),
                j.getSourceUrl(),
                j.getStatus(),
                j.getCreatedAt(),
                j.getStartedAt(),
                j.getFinishedAt(),
                j.getCreatedReviews(),
                j.getErrorMessage()
        );
    }
}
