package com.nexusclient.backend.profile;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.account.SignedInCheck;
import com.nexusclient.backend.clans.ClanService;
import com.nexusclient.backend.friends.FriendService;
import com.nexusclient.backend.live.LiveHub;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * ProfileController – profile cards and your own settings.
 *   GET /api/players/{name or uuid}   a profile card: name, playtime, favourite server, and (friends only)
 *                                      online status and server. The skin comes from Mojang by UUID.
 *   GET /api/me                        your own card plus your settings
 *   PUT /api/me/settings {showServer, dmPolicy, favouriteServer}
 *   POST /api/me/playtime {minutes}    the launcher reports play sessions (for the profile card)
 */
@RestController
public class ProfileController {

    public record Card(UUID uuid, String name, long playtimeMinutes, String favouriteServer, boolean friend,
                       boolean online, String server, String lastSeen) {}

    /**
     * Your own profile. premium = bought it or in a podium clan; premiumUntil is only for bought Premium.
     */
    public record Me(Card card, boolean showServer, Player.DmPolicy dmPolicy, Player.Role role, boolean premium,
                     String premiumUntil, boolean premiumFromPodium) {}

    public record SettingsRequest(Boolean showServer, Player.DmPolicy dmPolicy, @Size(max = 255) String favouriteServer) {}

    public record PlaytimeRequest(@Min(1) @Max(24 * 60) long minutes) {}

    private final PlayerRepository players;
    private final FriendService friends;
    private final LiveHub live;
    private final ClanService clans;

    public ProfileController(PlayerRepository players, FriendService friends, LiveHub live, ClanService clans) {
        this.players = players;
        this.friends = friends;
        this.live = live;
        this.clans = clans;
    }

    @GetMapping("/api/players/{who}")
    public Card card(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable String who) {
        return cardFor(find(who), me);
    }

    @GetMapping("/api/me")
    public Me me(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me) {
        boolean premium = clans.isPremium(me.getUuid());
        return new Me(cardFor(me, me.getUuid()), me.isShowServer(), me.getDmPolicy(), me.getRole(), premium,
                me.hasPaidPremium() ? me.getPremiumUntil().toString() : null, premium && !me.hasPaidPremium());
    }

    @PutMapping("/api/me/settings")
    @Transactional
    public Me settings(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @Valid @RequestBody SettingsRequest body) {
        if (body.showServer() != null) {
            me.setShowServer(body.showServer());
        }
        if (body.dmPolicy() != null) {
            me.setDmPolicy(body.dmPolicy());
        }
        if (body.favouriteServer() != null) {
            me.setFavouriteServer(body.favouriteServer().isBlank() ? null : body.favouriteServer().trim());
        }
        players.save(me);
        return me(me);
    }

    @PostMapping("/api/me/playtime")
    @Transactional
    public Map<String, Long> playtime(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me,
                                      @Valid @RequestBody PlaytimeRequest body) {
        me.addPlaytime(body.minutes());
        players.save(me);
        return Map.of("playtimeMinutes", me.getPlaytimeMinutes());
    }

    private Card cardFor(Player player, UUID viewer) {
        boolean self = player.getUuid().equals(viewer);
        boolean friend = !self && friends.areFriends(viewer, player.getUuid());
        boolean online = (self || friend) && live.isOnline(player.getUuid());
        String server = online && (self || player.isShowServer()) ? live.serverOf(player.getUuid()) : "";
        return new Card(player.getUuid(), player.getName(), player.getPlaytimeMinutes(), player.getFavouriteServer(),
                friend, online, server, self || friend ? String.valueOf(player.getLastSeen()) : null);
    }

    private Player find(String who) {
        try {
            return players.findById(UUID.fromString(who))
                    .orElseThrow(() -> NexusException.notFound("That player hasn't used Nexus"));
        } catch (IllegalArgumentException notAUuid) {
            return players.findFirstByNameIgnoreCaseOrderByLastSeenDesc(who)
                    .orElseThrow(() -> NexusException.notFound(who + " hasn't used Nexus"));
        }
    }
}
