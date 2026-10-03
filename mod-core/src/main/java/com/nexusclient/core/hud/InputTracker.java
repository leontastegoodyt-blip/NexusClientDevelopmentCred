package com.nexusclient.core.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * InputTracker – counts clicks (for CPS: clicks per second) and hits in a row (for the combo counter).
 * The loader module tells it when the player clicks, hits something, or gets hit.
 */
public final class InputTracker {

    private static final Deque<Long> LEFT = new ArrayDeque<Long>();
    private static final Deque<Long> RIGHT = new ArrayDeque<Long>();

    private static int combo;
    private static long lastHitMillis;
    private static final long COMBO_TIMEOUT_MS = 3000;

    private InputTracker() {}

    private static int totalClicks;

    /** A mouse click: 0 = left, 1 = right. */
    public static synchronized void click(int button) {
        totalClicks++;
        if (button > 1) {
            return; // middle and side buttons don't count for CPS
        }
        Deque<Long> clicks = button == 0 ? LEFT : RIGHT;
        clicks.addLast(System.currentTimeMillis());
        // Forget clicks older than a second (even when the CPS display isn't reading them).
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.removeFirst();
        }
    }

    /** Every click since the game started (for the clan honor activity report). */
    public static synchronized int totalClicks() {
        return totalClicks;
    }

    /** Clicks in the last second. */
    public static synchronized int cps(int button) {
        Deque<Long> clicks = button == 0 ? LEFT : RIGHT;
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.removeFirst();
        }
        return clicks.size();
    }

    /** The player hit another player/mob. */
    public static synchronized void hitSomething() {
        long now = System.currentTimeMillis();
        combo = now - lastHitMillis > COMBO_TIMEOUT_MS ? 1 : combo + 1;
        lastHitMillis = now;
    }

    /** The player got hit – that ends the combo. */
    public static synchronized void gotHit() {
        combo = 0;
    }

    /** Hits in a row without being hit back (resets after 3 seconds without a hit). */
    public static synchronized int combo() {
        if (System.currentTimeMillis() - lastHitMillis > COMBO_TIMEOUT_MS) {
            combo = 0;
        }
        return combo;
    }
}
