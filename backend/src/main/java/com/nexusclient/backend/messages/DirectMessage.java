package com.nexusclient.backend.messages;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** DirectMessage – one DM from one player to another. */
@Entity
@Table(name = "direct_messages")
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "from_uuid")
    private UUID from;
    @Column(name = "to_uuid")
    private UUID to;
    private String text;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected DirectMessage() {}

    public DirectMessage(UUID from, UUID to, String text) {
        this.from = from;
        this.to = to;
        this.text = text;
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getFrom() { return from; }
    public UUID getTo() { return to; }
    public String getText() { return text; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
}
