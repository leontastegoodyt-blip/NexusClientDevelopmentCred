package com.nexusclient.backend.account;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.NexusProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthService – signing in. See MojangVerifier for how ownership is checked. After that the player
 * gets a Nexus token (random, 256-bit) that the launcher and mod send with every request.
 */
@Service
public class AuthService {

    /** What a successful sign-in returns. */
    public record SignedIn(String token, UUID uuid, String name, Instant expiresAt) {}

    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(2);
    private static final int MAX_OPEN_CHALLENGES = 10_000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> challenges = new ConcurrentHashMap<>(); // server id → expiry
    private final PlayerRepository players;
    private final SessionRepository sessions;
    private final MojangVerifier mojang;
    private final NexusProperties properties;

    public AuthService(PlayerRepository players, SessionRepository sessions, MojangVerifier mojang,
                       NexusProperties properties) {
        this.players = players;
        this.sessions = sessions;
        this.mojang = mojang;
        this.properties = properties;
    }

    /** Step 1: a random "server id" for the launcher to "join" at Mojang. */
    public String challenge() {
        if (challenges.size() >= MAX_OPEN_CHALLENGES) {
            removeOldChallenges();
            if (challenges.size() >= MAX_OPEN_CHALLENGES) {
                throw new NexusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Too busy – try again soon");
            }
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String serverId = HexFormat.of().formatHex(bytes);
        challenges.put(serverId, Instant.now().plus(CHALLENGE_LIFETIME));
        return serverId;
    }

    /** Step 3: ask Mojang whether they really joined; if so, sign them in. */
    @Transactional
    public SignedIn verify(String username, String serverId) {
        Instant expiry = challenges.remove(serverId); // each code works once
        if (expiry == null || expiry.isBefore(Instant.now())) {
            throw NexusException.unauthorized("Sign-in code expired – try again");
        }
        MojangVerifier.Profile profile = mojang.hasJoined(username, serverId)
                .orElseThrow(() -> NexusException.unauthorized("Mojang couldn't confirm this account"));
        return signIn(profile.uuid(), profile.name());
    }

    /** Development only: sign in as any name without Mojang (nexus.dev-auth must be true). */
    @Transactional
    public SignedIn devSignIn(String name) {
        if (!properties.devAuth()) {
            throw NexusException.forbidden("Test sign-in is switched off on this server");
        }
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return signIn(offline, name);
    }

    private SignedIn signIn(UUID uuid, String name) {
        Player player = players.findById(uuid).orElseGet(() -> new Player(uuid, name));
        if (player.isBanned()) {
            throw NexusException.forbidden("Banned from Nexus online features until " + player.getBannedUntil()
                    + (player.getBanReason() == null ? "" : " (" + player.getBanReason() + ")"));
        }
        player.setName(name); // players can change their Minecraft name
        player.seenNow();
        if (properties.admins().contains(uuid)) {
            player.setRole(Player.Role.ADMIN);
        } else if (properties.moderators().contains(uuid)) {
            player.setRole(Player.Role.MODERATOR);
        } else if (player.isStaff()) {
            player.setRole(Player.Role.PLAYER); // removed from the staff list
        }
        players.save(player);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plus(Duration.ofDays(properties.sessionDays()));
        sessions.save(new Session(fingerprint(token), uuid, expires));
        return new SignedIn(token, uuid, name, expires);
    }

    /** The player a token belongs to, if it's valid, not expired, and they aren't banned. */
    public Optional<Player> playerFor(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findById(fingerprint(token))
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .flatMap(session -> players.findById(session.getPlayerUuid()))
                .filter(player -> !player.isBanned());
    }

    @Transactional
    public void signOut(String token) {
        sessions.deleteById(fingerprint(token));
    }

    /** Tidies up every 10 minutes: old sign-in codes and expired sessions. */
    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void cleanUp() {
        removeOldChallenges();
        sessions.deleteExpired(Instant.now());
    }

    private void removeOldChallenges() {
        Instant now = Instant.now();
        challenges.values().removeIf(expiry -> expiry.isBefore(now));
    }

    static String fingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
