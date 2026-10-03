package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** ClanPoll – a question for the clan to vote on (2 to 4 options, open for 24 hours). */
@Entity
@Table(name = "clan_polls")
public class ClanPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "clan_id")
    private Long clanId;
    private String question;
    private String options;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ClanPoll() {}

    public ClanPoll(Long clanId, String question, List<String> options, UUID createdBy, Instant endsAt) {
        this.clanId = clanId;
        this.question = question;
        this.options = String.join("\n", options);
        this.createdBy = createdBy;
        this.endsAt = endsAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getClanId() { return clanId; }
    public String getQuestion() { return question; }
    public List<String> getOptions() { return Arrays.asList(options.split("\n")); }
    public Instant getEndsAt() { return endsAt; }
    public boolean isOpen() { return endsAt.isAfter(Instant.now()); }
}
