package com.nexusclient.core.online;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emotes – short animations a player can play on their character (Nexus cosmetics, type EMOTE).
 *
 * Pressing the emote key (V) asks the Nexus server to play your emote (the one you wear in the
 * launcher's Skins → Nexus Cosmetics). The server tells every Nexus player on the same Minecraft
 * server, including you (live event "emote"), and each of their games calls play(). Only Nexus
 * players see emotes, like the other cosmetics.
 *
 * The animation itself lives here, so every Minecraft version moves the arm the same way: each
 * version's player-model hook asks rightArm() for the arm's angles while an emote plays.
 * So far there's one emote: Wave (the right arm goes up and waves side to side).
 */
public final class Emotes {

    private static final long WAVE_MS = 2400;     // how long a wave lasts
    private static final long EASE_MS = 250;      // raising / lowering the arm at the start and end

    private static final Map<UUID, Long> started = new ConcurrentHashMap<UUID, Long>();

    private Emotes() {}

    /** Starts an emote on a player (unknown emotes are ignored). */
    public static void play(UUID player, String emote) {
        if (player != null && "wave".equals(emote)) {
            started.put(player, System.currentTimeMillis());
        }
    }

    /**
     * The right arm's angles while this player waves, blended with its normal angles at the start
     * and end so it doesn't snap. Angles are Minecraft model angles in radians.
     *
     * @return {xRot, zRot}, or null when the player isn't playing an emote (leave the arm alone)
     */
    public static float[] rightArm(UUID player, float normalX, float normalZ) {
        Long start = player == null ? null : started.get(player);
        if (start == null) {
            return null;
        }
        long age = System.currentTimeMillis() - start;
        if (age >= WAVE_MS) {
            started.remove(player, start);
            return null;
        }
        // 0 → 1 while raising the arm, 1 while waving, 1 → 0 while lowering it.
        float weight = Math.min(1f, Math.min(age, WAVE_MS - age) / (float) EASE_MS);
        weight = weight * weight * (3 - 2 * weight); // smooth start and stop
        // The arm goes out to the side and up (Z turns it sideways), then swings side to side.
        float waveX = -0.25f;                                                               // slightly in front
        float waveZ = 2.45f + 0.4f * (float) Math.sin(age / 1000.0 * Math.PI * 2 * 2.2); // up and out, waving
        return new float[] {normalX + (waveX - normalX) * weight, normalZ + (waveZ - normalZ) * weight};
    }

    /** True while anyone is mid-emote (lets the hooks skip work almost always). */
    public static boolean anyPlaying() {
        return !started.isEmpty();
    }
}
