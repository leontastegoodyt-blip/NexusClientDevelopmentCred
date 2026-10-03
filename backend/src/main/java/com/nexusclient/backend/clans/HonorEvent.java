package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** HonorEvent – a special event from the Nexus owner: honor x multiplier between start and end. */
@Entity
@Table(name = "honor_events")
public class HonorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double multiplier;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "created_at")
    private Instant createdAt;

    protected HonorEvent() {}

    public HonorEvent(String name, double multiplier, Instant startsAt, Instant endsAt) {
        this.name = name;
        this.multiplier = multiplier;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public double getMultiplier() { return multiplier; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
}
