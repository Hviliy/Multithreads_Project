package ru.kopylov.multithreads.dto;

import lombok.*;

@Getter
@AllArgsConstructor
public class ParseResponse {
    private String url;
    private int created;
}
