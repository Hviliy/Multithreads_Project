package ru.kopylov.multithreads.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kopylov.multithreads.model.ParseJob;

import java.util.UUID;

public interface ParseJobRepository extends JpaRepository<ParseJob, UUID> {
}
