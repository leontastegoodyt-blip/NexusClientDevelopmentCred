package com.nexusclient.backend.workspace;

import com.google.gson.JsonParser;
import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * WorkspaceController – Mod Rerender: look at (and copy) another player's setup, if they allow it.
 *   PUT    /api/workspace {data}         share my workspace (the launcher sends it while sharing is on)
 *   DELETE /api/workspace                stop sharing (it's deleted from the server)
 *   GET    /api/workspace/{name or uuid} someone's shared workspace → {uuid, name, updatedAt, data}
 * Everyone must be signed in. A player who doesn't share simply "doesn't share their workspace".
 * "data" is made by the launcher (mods as Modrinth references, config text files); the server only
 * checks it's a JSON object and not too big.
 */
@RestController
public class WorkspaceController {

    private static final int MAX_CHARS = 2 * 1024 * 1024;

    public record ShareRequest(@NotNull String data) {}

    public record Shared(UUID uuid, String name, String updatedAt, String data) {}

    private final JdbcClient db;
    private final PlayerRepository players;

    public WorkspaceController(JdbcClient db, PlayerRepository players) {
        this.db = db;
        this.players = players;
    }

    @PutMapping("/api/workspace")
    public Map<String, Boolean> share(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody ShareRequest body) {
        if (body.data().length() > MAX_CHARS) {
            throw NexusException.badRequest("Your workspace is too big to share (over 2 MB)");
        }
        boolean object;
        try {
            object = JsonParser.parseString(body.data()).isJsonObject();
        } catch (RuntimeException e) {
            object = false;
        }
        if (!object) {
            throw NexusException.badRequest("A workspace must be a JSON object");
        }
        Timestamp now = Timestamp.from(Instant.now());
        int changed = db.sql("update workspaces set data = ?, updated_at = ? where player_uuid = ?")
                .param(body.data()).param(now).param(me).update();
        if (changed == 0) {
            db.sql("insert into workspaces (player_uuid, data, updated_at) values (?, ?, ?)")
                    .param(me).param(body.data()).param(now).update();
        }
        return Map.of("ok", true);
    }

    @DeleteMapping("/api/workspace")
    public Map<String, Boolean> stopSharing(@RequestAttribute(SignedInCheck.ME) UUID me) {
        db.sql("delete from workspaces where player_uuid = ?").param(me).update();
        return Map.of("ok", true);
    }

    @GetMapping("/api/workspace/{who}")
    public Shared get(@PathVariable String who) {
        Player player = find(who).orElseThrow(() -> NexusException.notFound(
                "Nobody called \"" + who + "\" has used Nexus yet"));
        record Row(String data, Timestamp updatedAt) {}
        Row row = db.sql("select data, updated_at from workspaces where player_uuid = ?").param(player.getUuid())
                .query((rs, n) -> new Row(rs.getString("data"), rs.getTimestamp("updated_at"))).optional()
                .orElseThrow(() -> NexusException.notFound(player.getName()
                        + " doesn't share their workspace (they can switch it on in Settings)"));
        return new Shared(player.getUuid(), player.getName(), row.updatedAt().toInstant().toString(), row.data());
    }

    private Optional<Player> find(String who) {
        String trimmed = who.trim();
        try {
            return players.findById(UUID.fromString(trimmed));
        } catch (IllegalArgumentException notUuid) {
            return players.findFirstByNameIgnoreCaseOrderByLastSeenDesc(trimmed);
        }
    }
}
