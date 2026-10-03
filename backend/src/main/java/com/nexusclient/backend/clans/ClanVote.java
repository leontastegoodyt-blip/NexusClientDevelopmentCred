package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

/** ClanVote – one member's vote in a poll (one vote each; voting again changes it). */
@Entity
@Table(name = "clan_votes")
@IdClass(ClanVote.Key.class)
public class ClanVote {

    public record Key(Long pollId, UUID player) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "poll_id")
    private Long pollId;
    @Id
    @Column(name = "player_uuid")
    private UUID player;
    private int choice;

    protected ClanVote() {}

    public ClanVote(Long pollId, UUID player, int choice) {
        this.pollId = pollId;
        this.player = player;
        this.choice = choice;
    }

    public Long getPollId() { return pollId; }
    public UUID getPlayer() { return player; }
    public int getChoice() { return choice; }
}
