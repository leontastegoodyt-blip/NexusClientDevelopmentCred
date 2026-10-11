package com.nexusclient.backend.sync;

import com.google.gson.JsonParser;
import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SettingsSyncController – the in-game Nexus settings (HUD layout, mod switches, colours…) saved to
 * the player's account, so they follow them to another PC (the project brief: "Settings sync").
 *   GET /api/me/mod-settings                      → {"savedAt": 1791…, "data": "{…}"}  (savedAt 0 = none yet)
 *   PUT /api/me/mod-settings {savedAt, data}      stores them, unless the account already has a NEWER
 *                                                 copy (then that newer copy is returned instead)
 * "data" is the settings file as text (must be a JSON object, up to 256 KB).
 */
@RestController
public class SettingsSyncController {

    private static final int MAX_CHARS = 256 * 1024;

    public record Saved(long savedAt, String data) {}

    public record SaveRequest(@NotNull @PositiveOrZero Long savedAt, @NotNull String data) {}

    private final JdbcClient db;

    public SettingsSyncController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping("/api/me/mod-settings")
    public Saved get(@RequestAttribute(SignedInCheck.ME) UUID me) {
        return stored(me).orElse(new Saved(0, ""));
    }

    @PutMapping("/api/me/mod-settings")
    public Saved save(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody SaveRequest body) {
        if (body.data().length() > MAX_CHARS) {
            throw NexusException.badRequest("Your settings are too big to sync (over 256 KB)");
        }
        try {
            if (!JsonParser.parseString(body.data()).isJsonObject()) {
                throw NexusException.badRequest("Settings must be a JSON object");
            }
        } catch (RuntimeException e) {
            if (e instanceof NexusException) {
                throw e;
            }
            throw NexusException.badRequest("Settings must be a JSON object");
        }
        // A copy from the future would block every real save, so it's capped at "now".
        long savedAt = Math.min(body.savedAt(), System.currentTimeMillis());
        Optional<Saved> current = stored(me);
        if (current.isPresent() && current.get().savedAt() >= savedAt) {
            return current.get(); // the account already has these or newer settings
        }
        Timestamp now = Timestamp.from(Instant.now());
        if (current.isPresent()) {
            db.sql("update mod_settings set data = ?, saved_at = ?, updated_at = ? where player_uuid = ?")
                    .param(body.data()).param(savedAt).param(now).param(me).update();
        } else {
            db.sql("insert into mod_settings (player_uuid, data, saved_at, updated_at) values (?, ?, ?, ?)")
                    .param(me).param(body.data()).param(savedAt).param(now).update();
        }
        return new Saved(savedAt, body.data());
    }

    private Optional<Saved> stored(UUID me) {
        return db.sql("select saved_at, data from mod_settings where player_uuid = ?").param(me)
                .query((row, n) -> new Saved(row.getLong("saved_at"), row.getString("data"))).optional();
    }
}
