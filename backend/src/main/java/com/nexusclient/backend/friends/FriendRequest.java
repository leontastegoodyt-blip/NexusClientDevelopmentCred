package com.nexusclient.backend.friends;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** FriendRequest – "from" asked "to" to be friends, and "to" hasn't answered yet. */
@Entity
@Table(name = "friend_requests")
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "from_uuid")
    private UUID from;
    @Column(name = "to_uuid")
    private UUID to;
    @Column(name = "created_at")
    private Instant createdAt;

    protected FriendRequest() {}

    public FriendRequest(UUID from, UUID to) {
        this.from = from;
        this.to = to;
        this.createdAt = Instant.now();
    }

    public UUID getFrom() { return from; }
    public UUID getTo() { return to; }
    public Instant getCreatedAt() { return createdAt; }
}
