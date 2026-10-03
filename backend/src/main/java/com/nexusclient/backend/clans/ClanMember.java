package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** ClanMember – a player in a clan, their rank, and how much honor they've earned for it. */
@Entity
@Table(name = "clan_members")
@IdClass(ClanMember.Key.class)
public class ClanMember {

    public record Key(Long clanId, UUID player) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "clan_id")
    private Long clanId;
    @Id
    @Column(name = "player_uuid")
    private UUID player;
    @Enumerated(EnumType.STRING)
    private ClanRank rank;
    private long honor;
    @Column(name = "joined_at")
    private Instant joinedAt;

    protected ClanMember() {}

    public ClanMember(Long clanId, UUID player, ClanRank rank) {
        this.clanId = clanId;
        this.player = player;
        this.rank = rank;
        this.joinedAt = Instant.now();
    }

    public Long getClanId() { return clanId; }
    public UUID getPlayer() { return player; }
    public ClanRank getRank() { return rank; }
    public void setRank(ClanRank rank) { this.rank = rank; }
    public long getHonor() { return honor; }
    public void addHonor(long amount) { this.honor = Math.max(0, honor + amount); }
    public Instant getJoinedAt() { return joinedAt; }
}
