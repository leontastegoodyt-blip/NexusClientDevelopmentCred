package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Clan – name, tag (shown next to members' names), description, owner and total honor. */
@Entity
@Table(name = "clans")
public class Clan {

    public static final int MAX_MEMBERS = 35;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String tag;
    private String description;
    @Column(name = "owner_uuid")
    private UUID owner;
    private long honor;
    private boolean mvp;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Clan() {}

    public Clan(String name, String tag, UUID owner) {
        this.name = name;
        this.tag = tag;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public long getHonor() { return honor; }
    public void addHonor(long amount) { this.honor = Math.max(0, honor + amount); }
    public boolean isMvp() { return mvp; }
    public void setMvp(boolean mvp) { this.mvp = mvp; }
    public Instant getCreatedAt() { return createdAt; }
}
