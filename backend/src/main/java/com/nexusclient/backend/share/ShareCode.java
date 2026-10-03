package com.nexusclient.backend.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** ShareCode – a short code (like NX-7K3P-9QWD) that stands for a shared setup, HUD layout or crosshair. */
@Entity
@Table(name = "share_codes")
public class ShareCode {

    public enum Kind { SETUP, HUD, CROSSHAIR }

    @Id
    private String code;
    private String kind;
    private String data;
    @Column(name = "owner_uuid")
    private UUID owner;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ShareCode() {}

    public ShareCode(String code, Kind kind, String data, UUID owner) {
        this.code = code;
        this.kind = kind.name();
        this.data = data;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public String getCode() { return code; }
    public String getKind() { return kind; }
    public String getData() { return data; }
}
