package ru.kopylov.multithreads.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import ru.kopylov.multithreads.model.SourceUrl;

import java.util.List;

public interface SourceUrlRepository extends JpaRepository<SourceUrl, Long> {
    List<SourceUrl> findAllByEnabledTrue();
}
