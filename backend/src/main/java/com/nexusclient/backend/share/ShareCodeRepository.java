package com.nexusclient.backend.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ShareCodeRepository extends JpaRepository<ShareCode, String> {

    long countByOwnerAndCreatedAtAfter(UUID owner, Instant after);
}
