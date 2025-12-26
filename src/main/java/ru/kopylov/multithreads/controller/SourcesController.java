package ru.kopylov.multithreads.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.kopylov.multithreads.dto.AddSourceRequest;
import ru.kopylov.multithreads.model.SourceUrl;
import ru.kopylov.multithreads.repository.SourceUrlRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sources")
public class SourcesController {

    private final SourceUrlRepository sourceUrlRepository;

    @PostMapping
    public SourceUrl add(@Valid @RequestBody AddSourceRequest req) {
        SourceUrl s = SourceUrl.builder()
                .url(req.getUrl())
                .enabled(true)
                .build();
        return sourceUrlRepository.save(s);
    }

    @GetMapping
    public List<SourceUrl> list() {
        return sourceUrlRepository.findAll();
    }

    @PostMapping("/{id}/enable")
    public SourceUrl enable(@PathVariable Long id) {
        var s = sourceUrlRepository.findById(id).orElseThrow();
        s.setEnabled(true);
        return sourceUrlRepository.save(s);
    }

    @PostMapping("/{id}/disable")
    public SourceUrl disable(@PathVariable Long id) {
        var s = sourceUrlRepository.findById(id).orElseThrow();
        s.setEnabled(false);
        return sourceUrlRepository.save(s);
    }
}
