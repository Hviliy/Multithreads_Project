package ru.kopylov.multithreads.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import ru.kopylov.multithreads.controller.SourceStubController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SteamReviewsClient {

    private static final Pattern APPID_IN_URL = Pattern.compile("/app/(\\d+)");
    private final RestTemplate restTemplate;

    public boolean supports(String input) {
        if (input == null) return false;
        String s = input.trim();
        return s.startsWith("steam:") || s.contains("store.steampowered.com/app/");
    }

    public String extractAppId(String input) {
        String s = input.trim();

        if (s.startsWith("steam:")) {
            String id = s.substring("steam:".length()).trim();
            if (!id.matches("\\d{2,10}")) {
                throw new IllegalArgumentException("Неверный appid после steam: " + id);
            }
            return id;
        }

        Matcher m = APPID_IN_URL.matcher(s);
        if (m.find()) return m.group(1);

        throw new IllegalArgumentException("Не Steam URL и не steam:<appid>: " + input);
    }


    public SteamPage fetchPage(String urlOrAppId, String cursor, int numPerPage) {
        String appId = extractAppId(urlOrAppId);

        String rawCursor = (cursor == null || cursor.isBlank()) ? "*" : cursor;

        String normalizedCursor = rawCursor.equals("*")
                ? "*"
                : UriUtils.decode(rawCursor, StandardCharsets.UTF_8);

        String encodedCursor = normalizedCursor.equals("*")
                ? "*"
                : URLEncoder.encode(normalizedCursor, StandardCharsets.UTF_8);


        String uri = "https://store.steampowered.com/appreviews/" + appId
                + "?json=1"
                + "&filter=funny"
                + "&language=russian"
                + "&review_type=all"
                + "&purchase_type=all"
                + "&num_per_page=" + Math.min(Math.max(numPerPage, 1), 100)
                + "&cursor=" + encodedCursor;


        SteamReviewsResponse resp = restTemplate.getForObject(uri, SteamReviewsResponse.class);
        if (resp == null) {
            throw new IllegalStateException("Стим вернул пустой ответ (null)");
        }
        if (resp.success != 1) {
            throw new IllegalStateException("Стим вернул некорректный ответ (success=" + resp.success + ")");
        }

        List<SourceStubController.RawReviewDto> mapped =
                (resp.reviews == null ? List.<SteamReview>of() : resp.reviews)
                        .stream()
                        .map(r -> {
                            int rating = Boolean.TRUE.equals(r.votedUp) ? 5 : 1;

                            LocalDate createdAt = Instant.ofEpochSecond(r.timestampCreated)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate();

                            String author = (r.author != null && r.author.steamId != null)
                                    ? r.author.steamId
                                    : "unknown";

                            String text = r.reviewText != null ? r.reviewText : "";

                            return new SourceStubController.RawReviewDto(author, rating, text, createdAt);
                        })
                        .toList();

        return new SteamPage(resp.cursor, mapped);
    }

    public record SteamPage(String nextCursor, List<SourceStubController.RawReviewDto> reviews) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamReviewsResponse {
        @JsonProperty("success")
        public int success;

        @JsonProperty("cursor")
        public String cursor;

        @JsonProperty("reviews")
        public List<SteamReview> reviews;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamReview {
        @JsonProperty("review")
        public String reviewText;

        @JsonProperty("timestamp_created")
        public long timestampCreated;

        @JsonProperty("voted_up")
        public Boolean votedUp;

        @JsonProperty("author")
        public SteamAuthor author;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamAuthor {
        @JsonProperty("steamid")
        public String steamId;
    }
}