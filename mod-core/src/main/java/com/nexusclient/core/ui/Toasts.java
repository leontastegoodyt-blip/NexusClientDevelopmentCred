package com.nexusclient.core.ui;

import com.nexusclient.core.platform.Renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Toasts – little pop-up cards in the bottom-right corner while playing ("LeonTasteyy is online",
 * "Party invite from …"). Each one slides in from the right edge, stays a few seconds, then slides
 * back out. At most three show at once; newer ones push older ones up.
 *
 * Safe to call {@link #show} from any thread (the live connection runs on its own); they're drawn by
 * NexusCore.renderHud on the game thread.
 */
public final class Toasts {

    private static final int WIDTH = 168;
    private static final int HEIGHT = 30;
    private static final long SLIDE_MS = 260;
    private static final long STAY_MS = 5500;
    private static final int MAX = 3;

    private static final class Toast {
        final String title;
        final String text;
        final String icon;
        final int colour;
        long shownAt = -1; // set when it's first drawn

        Toast(String title, String text, String icon, int colour) {
            this.title = title;
            this.text = text;
            this.icon = icon;
            this.colour = colour;
        }
    }

    private static final List<Toast> toasts = new ArrayList<Toast>();

    private Toasts() {}

    /**
     * Shows a pop-up.
     *
     * @param icon   one or two letters in the coloured circle (e.g. "F" for friends)
     * @param colour the card's accent colour (RGB)
     */
    public static void show(String title, String text, String icon, int colour) {
        synchronized (toasts) {
            toasts.add(0, new Toast(title, text, icon, colour));
            while (toasts.size() > 6) {
                toasts.remove(toasts.size() - 1);
            }
        }
    }

    /** Draws the pop-ups (called every frame). */
    public static void draw(Renderer r) {
        List<Toast> visible;
        synchronized (toasts) {
            if (toasts.isEmpty()) {
                return;
            }
            visible = new ArrayList<Toast>(toasts.subList(0, Math.min(MAX, toasts.size())));
        }
        long now = System.currentTimeMillis();
        int y = r.height() - 6 - HEIGHT; // bottom-right corner (usually empty), newest at the bottom
        for (Toast toast : visible) {
            if (toast.shownAt < 0) {
                toast.shownAt = now;
            }
            long age = now - toast.shownAt;
            if (age > STAY_MS + SLIDE_MS) {
                synchronized (toasts) {
                    toasts.remove(toast);
                }
                continue;
            }
            // 0 = off-screen to the right, 1 = fully in. Eased so it slows down as it arrives.
            double in = Math.min(1, age / (double) SLIDE_MS);
            double out = Math.max(0, (age - STAY_MS) / (double) SLIDE_MS);
            double shown = ease(in) * (1 - ease(out));
            int x = (int) Math.round(r.width() - 6 - WIDTH * shown - 6 * (1 - shown));
            drawCard(r, toast, x, y, age);
            y -= HEIGHT + 4;
        }
    }

    private static void drawCard(Renderer r, Toast toast, int x, int y, long age) {
        int accent = 0xFF000000 | toast.colour;
        UiKit.round(r, x, y, x + WIDTH, y + HEIGHT, 5, 0xEE0A0A12);
        UiKit.roundOutline(r, x, y, x + WIDTH, y + HEIGHT, 5, 0x50000000 | (toast.colour & 0xFFFFFF));
        // Accent bar on the left, and the icon circle.
        UiKit.round(r, x + 3, y + 5, x + 5, y + HEIGHT - 5, 1, accent);
        UiKit.round(r, x + 9, y + 6, x + 27, y + 24, 9, 0x30000000 | (toast.colour & 0xFFFFFF));
        UiKit.roundOutline(r, x + 9, y + 6, x + 27, y + 24, 9, accent);
        UiKit.centered(r, toast.icon, x + 18, y + 11, accent, false);
        // Title and text, cut to fit.
        r.text(UiKit.fit(r, toast.title, WIDTH - 40), x + 32, y + 6, UiKit.TEXT, false);
        r.text(UiKit.fit(r, toast.text, WIDTH - 40), x + 32, y + 17, UiKit.TEXT_DIM, false);
        // A thin line along the bottom that shrinks as time runs out.
        double left = 1 - Math.min(1, age / (double) STAY_MS);
        int barEnd = x + 6 + (int) ((WIDTH - 12) * left);
        if (barEnd > x + 6) {
            r.rect(x + 6, y + HEIGHT - 2, barEnd, y + HEIGHT - 1, 0x90000000 | (toast.colour & 0xFFFFFF));
        }
    }

    private static double ease(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }
}
