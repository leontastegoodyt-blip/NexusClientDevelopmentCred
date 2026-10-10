package com.nexusclient.backend.badges;

import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.clans.ClanService;
import com.nexusclient.backend.live.LiveHub;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BadgeController – the little icons in front of names in-game:
 *   nexus      – this player is using Nexus right now (the blue N hexagon)
 *   premium    – a Nexus Premium player (the gold N with a "+")
 *   developer  – the Nexus owner (the N hexagon plus a "DEV" tag)
 *
 *   GET /api/badges?uuids=a,b,c   public: {uuid: "nexus" | "premium" | "developer"}, only for players who have one
 *
 * "Using Nexus right now" = their launcher is connected (LiveHub) or their game reported activity in
 * the last few minutes (see markInGame, called by the honor activity report once a minute). Players
 * who aren't using Nexus at the moment get no badge, even if they used it before.
 */
@RestController
public class BadgeController {

    private static final Duration IN_GAME_WINDOW = Duration.ofMinutes(3);

    /** When each player's game last reported in. Kept in memory – it only matters for a few minutes. */
    private static final Map<UUID, Instant> lastInGame = new ConcurrentHashMap<>();

    private final PlayerRepository players;
    private final LiveHub live;
    private final ClanService clans;

    public BadgeController(PlayerRepository players, LiveHub live, ClanService clans) {
        this.players = players;
        this.live = live;
        this.clans = clans;
    }

    /** The game reported activity – this player is in-game with Nexus. */
    public static void markInGame(UUID player) {
        lastInGame.put(player, Instant.now());
    }

    @GetMapping("/api/badges")
    public Map<String, String> badges(@RequestParam String uuids) {
        List<UUID> list = Arrays.stream(uuids.split(",")).limit(200).map(String::trim).filter(s -> !s.isEmpty())
                .map(BadgeController::parse).filter(java.util.Objects::nonNull).toList();
        Map<String, String> result = new HashMap<>();
        Instant recent = Instant.now().minus(IN_GAME_WINDOW);
        for (Player player : players.findAllById(list)) {
            UUID uuid = player.getUuid();
            Instant seen = lastInGame.get(uuid);
            boolean usingNexus = live.isOnline(uuid) || (seen != null && seen.isAfter(recent));
            if (!usingNexus || player.isBanned()) {
                continue;
            }
            result.put(uuid.toString(), player.isDeveloper() ? "developer" : clans.isPremium(uuid) ? "premium" : "nexus");
        }
        return result;
    }

    private static UUID parse(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
