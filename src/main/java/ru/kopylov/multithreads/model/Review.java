package ru.kopylov.multithreads.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String sourceUrl;

    @Column(nullable = false, length = 255)
    private String authorName;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 5000)
    private String text;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(nullable = false)
    private Instant fetchedAt;
}
