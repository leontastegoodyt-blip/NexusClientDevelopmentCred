package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** ClanMessage – one clan chat message (kept so members can see what they missed). */
@Entity
@Table(name = "clan_messages")
public class ClanMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "clan_id")
    private Long clanId;
    @Column(name = "from_uuid")
    private UUID from;
    private String text;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected ClanMessage() {}

    public ClanMessage(Long clanId, UUID from, String text) {
        this.clanId = clanId;
        this.from = from;
        this.text = text;
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getFrom() { return from; }
    public String getText() { return text; }
    public Instant getSentAt() { return sentAt; }
}
