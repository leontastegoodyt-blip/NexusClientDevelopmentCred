package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** ClanInvite – a player was invited to a clan and hasn't answered yet. */
@Entity
@Table(name = "clan_invites")
@IdClass(ClanInvite.Key.class)
public class ClanInvite {

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
    @Column(name = "invited_by")
    private UUID invitedBy;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ClanInvite() {}

    public ClanInvite(Long clanId, UUID player, UUID invitedBy) {
        this.clanId = clanId;
        this.player = player;
        this.invitedBy = invitedBy;
        this.createdAt = Instant.now();
    }

    public Long getClanId() { return clanId; }
    public UUID getPlayer() { return player; }
}
