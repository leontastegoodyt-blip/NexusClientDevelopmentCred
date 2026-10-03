package com.nexusclient.backend.friends;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Block – "blocker" blocked "blocked": no friend requests, DMs or party invites between them. */
@Entity
@Table(name = "blocks")
@IdClass(Block.Key.class)
public class Block {

    public record Key(UUID blocker, UUID blocked) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "blocker_uuid")
    private UUID blocker;
    @Id
    @Column(name = "blocked_uuid")
    private UUID blocked;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Block() {}

    public Block(UUID blocker, UUID blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = Instant.now();
    }

    public UUID getBlocker() { return blocker; }
    public UUID getBlocked() { return blocked; }
}
