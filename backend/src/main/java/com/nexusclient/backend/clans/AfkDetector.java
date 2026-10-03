package com.nexusclient.backend.clans;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AfkDetector – decides, on the server, whether a minute of "playing" was real (the project brief: AFK
 * players lose honor instead of gaining it, and the final check must happen on the backend).
 *
 * Every minute the game sends a summary: how far the player moved, how much they turned the camera,
 * and how many keys, clicks and chat messages they sent. Two checks:
 *  1. A scoring model turns those numbers into "how likely is this a real player" (0–1). It's a
 *     small hand-tuned model for now (logistic regression); it can be swapped for a trained AI model
 *     later without changing anything else.
 *  2. Macro check: AFK tricks (auto-walkers, macros) repeat almost exactly the same numbers every
 *     minute. If 6 of the last 8 minutes look the same, it's AFK even when the score is high.
 */
@Component
public class AfkDetector {

    /** One minute of activity, as reported by the game. */
    public record Minute(double moveBlocks, double turnDegrees, int keyPresses, int clicks, int chatMessages,
                         int menuSeconds) {

        /** Numbers outside what a human can do are clamped (so fake reports can't game the score). */
        Minute clamped() {
            return new Minute(clamp(moveBlocks, 0, 2000), clamp(turnDegrees, 0, 50_000), (int) clamp(keyPresses, 0, 1500),
                    (int) clamp(clicks, 0, 1500), (int) clamp(chatMessages, 0, 60), (int) clamp(menuSeconds, 0, 60));
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    private static final int HISTORY = 8;
    private final Map<UUID, Deque<Minute>> history = new ConcurrentHashMap<>();

    /** True if this minute counts as really playing. */
    public boolean isActive(UUID player, Minute raw) {
        Minute m = raw.clamped();
        Deque<Minute> recent = history.computeIfAbsent(player, key -> new ArrayDeque<>());
        synchronized (recent) {
            recent.addLast(m);
            while (recent.size() > HISTORY) {
                recent.removeFirst();
            }
            if (looksLikeAMacro(recent)) {
                return false;
            }
        }
        return score(m) >= 0.5;
    }

    /** The scoring model: 0 = surely AFK, 1 = surely playing. */
    static double score(Minute m) {
        int inputs = m.keyPresses() + m.clicks() + m.chatMessages() * 3;
        if (inputs == 0) {
            return 0; // not a single key or click in a whole minute
        }
        double z = -3.2
                + 0.90 * Math.log1p(inputs)
                + 0.55 * Math.log1p(m.turnDegrees() / 10)
                + 0.35 * Math.log1p(m.moveBlocks())
                - 1.5 * (m.menuSeconds() >= 50 ? 1 : 0); // sat in a menu nearly the whole minute
        return 1 / (1 + Math.exp(-z));
    }

    /** 6 or more of the last 8 minutes (nearly) identical = a macro or AFK machine. */
    private static boolean looksLikeAMacro(Deque<Minute> recent) {
        if (recent.size() < 6) {
            return false;
        }
        Minute newest = recent.peekLast();
        int same = 0;
        for (Minute m : recent) {
            if (close(m.keyPresses() + m.clicks(), newest.keyPresses() + newest.clicks())
                    && close(m.turnDegrees(), newest.turnDegrees()) && close(m.moveBlocks(), newest.moveBlocks())) {
                same++;
            }
        }
        return same >= 6 && newest.keyPresses() + newest.clicks() > 0;
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= Math.max(1, Math.max(a, b) * 0.03);
    }
}
