package ru.kopylov.multithreads.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import ru.kopylov.multithreads.dto.JobIdResponse;
import ru.kopylov.multithreads.dto.JobResponse;
import ru.kopylov.multithreads.dto.ParseRequest;
import ru.kopylov.multithreads.dto.ReviewResponse;
import ru.kopylov.multithreads.service.ReviewService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Парсинг и получение отзывов")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/parse")
    public JobIdResponse parse(@Valid @RequestBody ParseRequest request) {
        UUID jobId = reviewService.startParseAsync(request.getUrl());
        return new JobIdResponse(jobId);
    }

    @GetMapping("/jobs/{id}")
    public JobResponse job(@PathVariable UUID id) {
        return JobResponse.from(reviewService.getJob(id));
    }

    @GetMapping("/answer")
    public Page<ReviewResponse> answer(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return reviewService.getReviews(pageable).map(ReviewResponse::from);
    }
}