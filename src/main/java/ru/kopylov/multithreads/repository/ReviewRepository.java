package ru.kopylov.multithreads.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kopylov.multithreads.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("select r.dedupKey from Review r where r.sourceUrl = :sourceUrl and r.dedupKey in :keys")
    List<String> findExistingDedupKeys(@Param("sourceUrl") String sourceUrl,
                                       @Param("keys") Collection<String> keys);
}
