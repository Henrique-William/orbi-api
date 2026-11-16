package com.tech.orbi.Repository;

import com.tech.orbi.entity.Benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BenchmarkRepository extends JpaRepository<Benchmark, Long> {

    List<Benchmark> findBySessionIdOrderByAlgorithmNameAsc(String sessionId);

    List<Benchmark> findBySessionIdOrderByIdAsc(String sessionId);

    List<Benchmark> findByBatchIdOrderByIdAsc(String batchId);

    @Query("SELECT b.batchId FROM Benchmark b WHERE b.batchId IS NOT NULL GROUP BY b.batchId ORDER BY MAX(b.id) DESC")
    List<String> findDistinctBatchIds();

    List<Benchmark> findTop200ByOrderByCreatedAtDesc();

    Optional<Benchmark> findTopByOrderByCreatedAtDesc();

    @Query("SELECT b.sessionId FROM Benchmark b GROUP BY b.sessionId ORDER BY MAX(b.id) DESC")
    List<String> findDistinctSessionIds();

    List<Benchmark> findAllByOrderByIdDesc();

    void deleteBySessionId(String sessionId);
}
