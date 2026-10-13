package com.nexusclient.backend.cosmetics;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.RateLimiter;
import com.nexusclient.backend.live.EmoteRequested;
import com.nexusclient.backend.live.Event;
import com.nexusclient.backend.live.LiveHub;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * EmoteRelay – plays a player's emote (e.g. Wave) on the screens of the Nexus players near them.
 *
 * The game sends {"type": "emote"} over the live connection when the emote key is pressed. The
 * server looks up which emote that player wears (Skins → Nexus Cosmetics in the launcher) and sends
 * {"type": "emote", "player": uuid, "emote": "wave"} to everyone on the same Minecraft server,
 * including the player (so their own game plays it too). Nobody else learns anything new: they're
 * on the same server, so they can already see that player. Limit: one emote every 3 seconds.
 */
@Component
public class EmoteRelay {

    private final JdbcClient db;
    private final LiveHub hub;
    private final RateLimiter limits;

    public EmoteRelay(JdbcClient db, LiveHub hub, RateLimiter limits) {
        this.db = db;
        this.hub = hub;
        this.limits = limits;
    }

    @EventListener
    public void onEmote(EmoteRequested request) {
        UUID player = request.player();
        String server = hub.serverOf(player);
        if (server.isEmpty()) {
            return; // menus / singleplayer: nobody else could see it
        }
        try {
            limits.check("emote:" + player, 1, Duration.ofSeconds(3), "Slow down");
        } catch (NexusException tooFast) {
            return;
        }
        String emote = db.sql("select cosmetic_id from cosmetic_equipped where player_uuid = ? and type = 'EMOTE'")
                .param(player).query(String.class).optional().orElse(null);
        if (emote == null) {
            return;
        }
        Event event = Event.of("emote").with("player", player).with("emote", emote);
        for (UUID other : hub.playersOn(server)) {
            hub.send(other, event);
        }
    }
}
