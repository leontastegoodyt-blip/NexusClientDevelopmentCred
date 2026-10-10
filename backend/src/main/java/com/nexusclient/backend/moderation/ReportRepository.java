package com.nexusclient.backend.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findTop100ByStatusOrderByCreatedAtAsc(String status);

    long countByReporterAndCreatedAtAfter(UUID reporter, Instant after);

    long countByStatus(String status);
}
