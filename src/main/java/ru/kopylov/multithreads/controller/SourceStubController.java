package ru.kopylov.multithreads.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/stub")
public class SourceStubController {

    @GetMapping("/reviews")
    public List<RawReviewDto> getReviews(@RequestParam String url,
                                         @RequestParam(defaultValue = "5") int count,
                                         @RequestParam(defaultValue = "0") int page) {
        var rnd = ThreadLocalRandom.current();
        int pageNo;
        pageNo = page;

        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    int rating = rnd.nextInt(1, 6);
                    String author = "стаб юзер" + pageNo + "_" + rnd.nextInt(1000, 9999);
                    String text = "стаб страница отзыва=" + pageNo + " для " + url + " #" + rnd.nextInt(10000);
                    LocalDate createdAt = LocalDate.now().minusDays(rnd.nextInt(0, 365));
                    return new RawReviewDto(author, rating, text, createdAt);
                })
                .toList();
    }

    @Getter
    @AllArgsConstructor
    public static class RawReviewDto {
        private String authorName;
        private int rating;
        private String text;
        private LocalDate createdAt;
    }
}
