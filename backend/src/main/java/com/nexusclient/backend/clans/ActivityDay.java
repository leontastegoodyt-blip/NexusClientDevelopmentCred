package com.nexusclient.backend.clans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** ActivityDay – one player's day: minutes judged active or AFK, and the honor earned or lost. */
@Entity
@Table(name = "activity_days")
@IdClass(ActivityDay.Key.class)
public class ActivityDay {

    public record Key(UUID player, LocalDate day) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "player_uuid")
    private UUID player;
    @Id
    @Column(name = "activity_date")
    private LocalDate day;
    @Column(name = "active_minutes")
    private int activeMinutes;
    @Column(name = "counted_minutes")
    private int countedMinutes;
    @Column(name = "afk_minutes")
    private int afkMinutes;
    private long honor;
    @Column(name = "last_report_at")
    private Instant lastReportAt;

    protected ActivityDay() {}

    public ActivityDay(UUID player, LocalDate day) {
        this.player = player;
        this.day = day;
    }

    public UUID getPlayer() { return player; }
    public LocalDate getDay() { return day; }
    public int getActiveMinutes() { return activeMinutes; }
    public int getCountedMinutes() { return countedMinutes; }
    public int getAfkMinutes() { return afkMinutes; }
    public long getHonor() { return honor; }
    public Instant getLastReportAt() { return lastReportAt; }

    public void reported(Instant at) { this.lastReportAt = at; }
    public void addActive(boolean counted) {
        activeMinutes++;
        if (counted) {
            countedMinutes++;
        }
    }
    public void addAfk() { afkMinutes++; }
    public void addHonor(long amount) { honor += amount; }
}
