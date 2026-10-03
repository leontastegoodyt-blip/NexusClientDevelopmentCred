package com.nexusclient.backend.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Session – one sign-in. The launcher/mod sends the token with every request; the database only
 * keeps its SHA-256 fingerprint, so a leaked database can't be used to sign in as anyone.
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "player_uuid")
    private UUID playerUuid;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Session() {}

    public Session(String tokenHash, UUID playerUuid, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.playerUuid = playerUuid;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public Instant getExpiresAt() { return expiresAt; }
}
