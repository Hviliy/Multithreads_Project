package ru.kopylov.multithreads.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.kopylov.multithreads.dto.ReviewResponse;
import ru.kopylov.multithreads.repository.ReviewRepository;

import java.util.Comparator;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/answer")
public class ParallelAnswerController {

    private final ReviewRepository reviewRepository;

    @GetMapping("/parallel")
    public ParallelPage<ReviewResponse> parallel(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(defaultValue = "fetchedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        var all = reviewRepository.findAll();

        Comparator<ru.kopylov.multithreads.model.Review> cmp = switch (sortBy) {
            case "rating" -> Comparator.comparingInt(ru.kopylov.multithreads.model.Review::getRating);
            case "createdAt" -> Comparator.comparing(ru.kopylov.multithreads.model.Review::getCreatedAt);
            default -> Comparator.comparing(ru.kopylov.multithreads.model.Review::getFetchedAt);
        };

        if ("desc".equalsIgnoreCase(direction)) {
            cmp = cmp.reversed();
        }

        var filteredSorted = all.parallelStream()
                .filter(r -> minRating == null || r.getRating() >= minRating)
                .sorted(cmp)
                .map(ReviewResponse::from)
                .toList();

        int total = filteredSorted.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        List<ReviewResponse> content = filteredSorted.subList(from, to);

        return new ParallelPage<>(content, page, size, total);
    }

    public record ParallelPage<T>(List<T> content, int page, int size, int totalElements) {
        public int totalPages() {
            return (int) Math.ceil((double) totalElements / (double) size);
        }
    }
}
