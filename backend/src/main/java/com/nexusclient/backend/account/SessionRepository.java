package com.nexusclient.backend.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, String> {

    @Modifying
    @Query("delete from Session s where s.expiresAt < :now")
    int deleteExpired(Instant now);

    @Modifying
    @Query("delete from Session s where s.playerUuid = :player")
    int deleteAllFor(UUID player);
}
