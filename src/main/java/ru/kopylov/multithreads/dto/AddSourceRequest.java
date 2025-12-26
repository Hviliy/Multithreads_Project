package ru.kopylov.multithreads.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddSourceRequest {
    @NotBlank
    private String url;
}
