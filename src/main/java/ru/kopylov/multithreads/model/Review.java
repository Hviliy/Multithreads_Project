package ru.kopylov.multithreads.model;

import jakarta.persistence.*;
import lombok.*;
import ru.kopylov.multithreads.util.DedupUtils;

import java.time.Instant;


@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reviews_source_dedup", columnNames = {"source_url", "dedup_key"})
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String text;

    private java.time.LocalDate createdAt;

    @Column(nullable = false)
    private java.time.Instant fetchedAt;

    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;

    @PrePersist
    public void prePersist() {
        if (this.fetchedAt == null) {
            this.fetchedAt = Instant.now();
        }

        if (this.dedupKey == null || this.dedupKey.isBlank()) {
            String normAuthor = (this.authorName == null) ? "" : this.authorName.trim();
            String normText   = (this.text == null) ? "" : this.text.trim();
            String normDate   = (this.createdAt == null) ? "" : this.createdAt.toString();

            String material = normAuthor + "|" + normDate + "|" + this.rating + "|" + normText;
            this.dedupKey = DedupUtils.sha256Hex(material);
        }
    }
}