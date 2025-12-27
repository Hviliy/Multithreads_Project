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

    public List<RawReviewDto> fetchRawReviews(String url, int count, int page) {
        String endpoint = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host("localhost")
                .port(port)
                .path("/stub/reviews")
                .queryParam("url", url)
                .queryParam("count", count)
                .queryParam("page", page)
                .toUriString();

        RawReviewDto[] arr = restTemplate.getForObject(endpoint, RawReviewDto[].class);
        return arr == null ? List.of() : Arrays.asList(arr);
    }
}
