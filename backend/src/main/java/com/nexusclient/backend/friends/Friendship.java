package com.nexusclient.backend.friends;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Friendship – "player" is friends with "friend" (each friendship is stored both ways). */
@Entity
@Table(name = "friendships")
@IdClass(Friendship.Key.class)
public class Friendship {

    /** The two UUIDs together identify one row. */
    public record Key(UUID player, UUID friend) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "player_uuid")
    private UUID player;
    @Id
    @Column(name = "friend_uuid")
    private UUID friend;
    private Instant since;

    protected Friendship() {}

    public Friendship(UUID player, UUID friend) {
        this.player = player;
        this.friend = friend;
        this.since = Instant.now();
    }

    public UUID getPlayer() { return player; }
    public UUID getFriend() { return friend; }
    public Instant getSince() { return since; }
}
