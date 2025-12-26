package ru.kopylov.multithreads.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ParseRequest", description = "Запрос на запуск парсинга отзывов по URL")
public class ParseRequest {

    @NotBlank(message = "url не должен быть пустым")
    @Schema(
            description = "Полный URL страницы товара с отзывами",
            example = "https://example.com/product/123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String url;
}

