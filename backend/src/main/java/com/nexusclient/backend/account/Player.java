package com.nexusclient.backend.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Player – one row of the "players" table: someone who has signed in to Nexus. */
@Entity
@Table(name = "players")
public class Player {

    public enum Role { PLAYER, MODERATOR, ADMIN }

    /** Who may send this player direct messages. Friends-only by default (the project brief: safety). */
    public enum DmPolicy { FRIENDS, NOBODY }

    @Id
    private UUID uuid;
    private String name;
    @Enumerated(EnumType.STRING)
    private Role role = Role.PLAYER;
    @Column(name = "show_server")
    private boolean showServer = true;
    @Enumerated(EnumType.STRING)
    @Column(name = "dm_policy")
    private DmPolicy dmPolicy = DmPolicy.FRIENDS;
    @Column(name = "playtime_minutes")
    private long playtimeMinutes;
    @Column(name = "favourite_server")
    private String favouriteServer;
    @Column(name = "banned_until")
    private Instant bannedUntil;
    @Column(name = "ban_reason")
    private String banReason;
    @Column(name = "muted_until")
    private Instant mutedUntil;
    @Column(name = "primary_clan_id")
    private Long primaryClanId;       // the clan this player's honor goes to
    @Column(name = "premium_until")
    private Instant premiumUntil;     // bought Nexus Premium (podium clans get it free, see ClanService)
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "last_seen")
    private Instant lastSeen;

    protected Player() {} // for the database library

    public Player(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.createdAt = Instant.now();
        this.lastSeen = createdAt;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isShowServer() { return showServer; }
    public void setShowServer(boolean showServer) { this.showServer = showServer; }
    public DmPolicy getDmPolicy() { return dmPolicy; }
    public void setDmPolicy(DmPolicy dmPolicy) { this.dmPolicy = dmPolicy; }
    public long getPlaytimeMinutes() { return playtimeMinutes; }
    public void addPlaytime(long minutes) { this.playtimeMinutes += minutes; }
    public String getFavouriteServer() { return favouriteServer; }
    public void setFavouriteServer(String favouriteServer) { this.favouriteServer = favouriteServer; }
    public Instant getBannedUntil() { return bannedUntil; }
    public String getBanReason() { return banReason; }
    public Instant getMutedUntil() { return mutedUntil; }
    public Instant getLastSeen() { return lastSeen; }
    public void seenNow() { this.lastSeen = Instant.now(); }

    public boolean isBanned() {
        return bannedUntil != null && bannedUntil.isAfter(Instant.now());
    }

    public boolean isMuted() {
        return mutedUntil != null && mutedUntil.isAfter(Instant.now());
    }

    public void ban(Instant until, String reason) {
        this.bannedUntil = until;
        this.banReason = reason;
    }

    public void mute(Instant until) {
        this.mutedUntil = until;
    }

    public Long getPrimaryClanId() { return primaryClanId; }
    public void setPrimaryClanId(Long clanId) { this.primaryClanId = clanId; }
    public Instant getPremiumUntil() { return premiumUntil; }
    public void setPremiumUntil(Instant until) { this.premiumUntil = until; }

    public boolean hasPaidPremium() {
        return premiumUntil != null && premiumUntil.isAfter(Instant.now());
    }

    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.MODERATOR;
    }
}
