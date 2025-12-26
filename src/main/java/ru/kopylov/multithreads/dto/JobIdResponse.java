package ru.kopylov.multithreads.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class JobIdResponse {
    private UUID jobId;
}
