package ru.kopylov.multithreads.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.kopylov.multithreads.controller.SourceStubController.RawReviewDto;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SourceClient {

    private final RestTemplate restTemplate;

    @Value("${server.port:8080}")
    private int port;

    public List<RawReviewDto> fetchRawReviews(String url, int count) {
        String endpoint = UriComponentsBuilder
                .fromUriString("http://localhost:" + port + "/stub/reviews")
                .queryParam("url", url)
                .queryParam("count", count)
                .toUriString();

        RawReviewDto[] arr = restTemplate.getForObject(endpoint, RawReviewDto[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }
}
