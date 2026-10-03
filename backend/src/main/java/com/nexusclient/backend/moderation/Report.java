package com.nexusclient.backend.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Report – a player reported another player; moderators look at open reports and close them. */
@Entity
@Table(name = "reports")
public class Report {

    public enum Reason { CHAT, NAME, SKIN, CHEATING, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reporter_uuid")
    private UUID reporter;
    @Column(name = "target_uuid")
    private UUID target;
    private String reason;
    private String details;
    private String evidence;
    private String status = "OPEN";
    @Column(name = "handled_by")
    private UUID handledBy;
    @Column(name = "handled_at")
    private Instant handledAt;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Report() {}

    public Report(UUID reporter, UUID target, Reason reason, String details, String evidence) {
        this.reporter = reporter;
        this.target = target;
        this.reason = reason.name();
        this.details = details;
        this.evidence = evidence;
        this.createdAt = Instant.now();
    }

    public void close(UUID moderator) {
        this.status = "CLOSED";
        this.handledBy = moderator;
        this.handledAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getReporter() { return reporter; }
    public UUID getTarget() { return target; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public String getEvidence() { return evidence; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
