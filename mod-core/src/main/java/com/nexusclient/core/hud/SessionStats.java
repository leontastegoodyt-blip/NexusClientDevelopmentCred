package com.nexusclient.core.hud;

import java.util.Locale;

/**
 * SessionStats – PvP stats for this play session (since the game started): kills, deaths, kill
 * streak and wins. Nothing is saved – each game session starts at zero.
 *
 * The loader module reports what happens: kill() when a player we hit dies, death() when we die,
 * chatMessage() for every chat line (to spot "you won" messages).
 */
public final class SessionStats {

    private static int kills;
    private static int deaths;
    private static int streak;
    private static int bestStreak;
    private static int wins;
    private static long lastWinMillis;

    private SessionStats() {}

    public static synchronized void kill() {
        kills++;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
    }

    public static synchronized void death() {
        deaths++;
        streak = 0;
    }

    /** Counts a win if the chat line contains one of the (comma separated) win messages. */
    public static synchronized void chatMessage(String text, String winTriggers) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String trigger : winTriggers.split(",")) {
            String t = trigger.trim().toLowerCase(Locale.ROOT);
            // Servers often print the win message several times – count it once per 10 seconds.
            if (!t.isEmpty() && lower.contains(t) && System.currentTimeMillis() - lastWinMillis > 10_000) {
                wins++;
                lastWinMillis = System.currentTimeMillis();
                return;
            }
        }
    }

    public static synchronized void reset() {
        kills = deaths = streak = bestStreak = wins = 0;
    }

    public static synchronized int kills() { return kills; }
    public static synchronized int deaths() { return deaths; }
    public static synchronized int streak() { return streak; }
    public static synchronized int bestStreak() { return bestStreak; }
    public static synchronized int wins() { return wins; }

    /** Kills per death, e.g. "2.50" (just the kills if there are no deaths yet). */
    public static synchronized String kd() {
        return deaths == 0 ? String.valueOf(kills) : String.format(Locale.ROOT, "%.2f", kills / (double) deaths);
    }
}
