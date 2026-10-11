package com.nexusclient.core.ui;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.platform.Renderer;

/**
 * UiKit – the Nexus look for in-game menus: dark rounded-looking panels, neon accent, switches,
 * sliders and buttons. Every in-game menu draws with these, so they all match the launcher's style.
 */
public final class UiKit {

    public static final int BACKDROP = 0xB0050508;     // darkens the game behind menus
    public static final int PANEL = 0xF20D0D14;        // the main panel
    public static final int PANEL_2 = 0xFF12121B;      // cards
    public static final int PANEL_3 = 0xFF1A1A26;      // hover
    public static final int BORDER = 0xFF23233A;
    public static final int TEXT = 0xFFE4E4EE;
    public static final int TEXT_DIM = 0xFF7C7C96;
    public static final int TEXT_FAINT = 0xFF4A4A60;
    public static final int DANGER = 0xFFFF6B5E;

    private UiKit() {}

    /** The accent colour (players can change it in Nexus Settings). */
    public static int accent() {
        return NexusCore.get().settings().accent.argb();
    }

    /** The accent colour, but see-through (alpha 0–255). */
    public static int accent(int alpha) {
        return (alpha << 24) | (accent() & 0xFFFFFF);
    }

    public static boolean inside(double x, double y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x < x2 && y >= y1 && y < y2;
    }

    /** A box with a 1-pixel border. */
    public static void box(Renderer r, int x1, int y1, int x2, int y2, int fill, int border) {
        r.rect(x1, y1, x2, y2, fill);
        r.outline(x1, y1, x2, y2, border);
    }

    /** A button. Returns nothing – check clicks with inside(). */
    public static void button(Renderer r, String label, int x1, int y1, int x2, int y2, boolean hovered, boolean primary) {
        if (primary) {
            box(r, x1, y1, x2, y2, hovered ? accent(0xFF) : accent(0xD0), accent(0xFF));
            centered(r, label, (x1 + x2) / 2, y1 + (y2 - y1 - r.lineHeight()) / 2 + 1, 0xFF06060A, false);
        } else {
            box(r, x1, y1, x2, y2, hovered ? PANEL_3 : PANEL_2, hovered ? accent(0x90) : BORDER);
            centered(r, label, (x1 + x2) / 2, y1 + (y2 - y1 - r.lineHeight()) / 2 + 1, hovered ? TEXT : TEXT_DIM, false);
        }
    }

    /** An on/off switch, 20 x 10 pixels, with its left edge at x. */
    public static void toggle(Renderer r, int x, int y, boolean on, boolean hovered) {
        box(r, x, y, x + 20, y + 10, on ? accent(0x50) : PANEL_3, on ? accent() : (hovered ? TEXT_DIM : BORDER));
        int knob = on ? x + 11 : x + 1;
        r.rect(knob, y + 1, knob + 8, y + 9, on ? accent() : TEXT_DIM);
    }

    /** A slider from x1 to x2 at the given fill fraction (0–1). */
    public static void slider(Renderer r, int x1, int y, int x2, double fraction, boolean hovered) {
        r.rect(x1, y + 4, x2, y + 6, PANEL_3);
        int filled = x1 + (int) Math.round((x2 - x1) * fraction);
        r.rect(x1, y + 4, filled, y + 6, accent());
        r.rect(filled - 2, y + 1, filled + 2, y + 9, hovered ? 0xFFFFFFFF : accent());
    }

    /** A text box. Shows a blinking cursor when focused. */
    public static void textField(Renderer r, String text, String placeholder, int x1, int y1, int x2, int y2, boolean focused) {
        box(r, x1, y1, x2, y2, 0xFF08080D, focused ? accent() : BORDER);
        String shown = text.isEmpty() && !focused ? placeholder : text;
        int colour = text.isEmpty() && !focused ? TEXT_FAINT : TEXT;
        // Show the end of long text, so what you type is always visible.
        int maxWidth = x2 - x1 - 8;
        while (r.textWidth(shown) > maxWidth && shown.length() > 1) {
            shown = shown.substring(1);
        }
        int textY = y1 + (y2 - y1 - r.lineHeight()) / 2 + 1;
        r.text(shown, x1 + 4, textY, colour, false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x1 + 4 + r.textWidth(shown);
            r.rect(cursorX, textY - 1, cursorX + 1, textY + r.lineHeight(), accent());
        }
    }

    // ------------------------------------------------------------------ rounded, modern pieces

    /**
     * A filled rectangle with rounded corners. Minecraft can only draw straight rectangles, so the
     * corners are made from thin strips that get shorter towards the corner (a tiny circle shape).
     */
    public static void round(Renderer r, int x1, int y1, int x2, int y2, int radius, int colour) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (radius <= 0) {
            r.rect(x1, y1, x2, y2, colour);
            return;
        }
        r.rect(x1, y1 + radius, x2, y2 - radius, colour);
        for (int i = 0; i < radius; i++) {
            double d = radius - i - 0.5;
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - d * d));
            r.rect(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, colour);
            r.rect(x1 + inset, y2 - 1 - i, x2 - inset, y2 - i, colour);
        }
    }

    /** A rounded box with a 1-pixel rounded border. Works with see-through fills too. */
    public static void roundBox(Renderer r, int x1, int y1, int x2, int y2, int radius, int fill, int border) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        round(r, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), fill);
        roundOutline(r, x1, y1, x2, y2, radius, border);
    }

    /** Just the 1-pixel edge of a rounded rectangle (nothing drawn inside it). */
    public static void roundOutline(Renderer r, int x1, int y1, int x2, int y2, int radius, int colour) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (radius <= 0) {
            r.outline(x1, y1, x2, y2, colour);
            return;
        }
        // How far in the edge is on each of the corner rows.
        int[] inset = new int[radius];
        for (int i = 0; i < radius; i++) {
            double d = radius - i - 0.5;
            inset[i] = radius - (int) Math.round(Math.sqrt(radius * radius - d * d));
        }
        // Top and bottom edges.
        r.rect(x1 + inset[0], y1, x2 - inset[0], y1 + 1, colour);
        r.rect(x1 + inset[0], y2 - 1, x2 - inset[0], y2, colour);
        // The curved corners: on each row, the pixels between this row's edge and the row above's.
        for (int i = 1; i < radius; i++) {
            int from = inset[i];
            int to = Math.max(inset[i] + 1, inset[i - 1]);
            r.rect(x1 + from, y1 + i, x1 + to, y1 + i + 1, colour);
            r.rect(x2 - to, y1 + i, x2 - from, y1 + i + 1, colour);
            r.rect(x1 + from, y2 - 1 - i, x1 + to, y2 - i, colour);
            r.rect(x2 - to, y2 - 1 - i, x2 - from, y2 - i, colour);
        }
        // The straight sides.
        r.rect(x1, y1 + radius, x1 + 1, y2 - radius, colour);
        r.rect(x2 - 1, y1 + radius, x2, y2 - radius, colour);
    }

    /** A thin line that fades from light blue through logo blue to violet, like the Nexus logo. */
    public static void gradientLine(Renderer r, int x1, int y, int x2, int alpha) {
        int steps = Math.max(1, (x2 - x1) / 3);
        for (int i = 0; i < steps; i++) {
            int sx1 = x1 + (x2 - x1) * i / steps;
            int sx2 = x1 + (x2 - x1) * (i + 1) / steps;
            r.rect(sx1, y, sx2, y + 1, (alpha << 24) | gradient(i / (double) steps));
        }
    }

    /** The logo gradient at a point from 0 (left, light blue) to 1 (right, violet). RGB only. */
    public static int gradient(double t) {
        return t < 0.45 ? mix(0x5FD3FF, accent() & 0xFFFFFF, t / 0.45) : mix(accent() & 0xFFFFFF, 0x8B5CFF, (t - 0.45) / 0.55);
    }

    /** Blends two RGB colours (t = 0 → a, t = 1 → b). */
    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int red = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int green = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (red << 16) | (green << 8) | blue;
    }

    /** The Nexus hexagon logo (the same pixel art as the name-tag badge), 16 x 16 at (x, y). */
    public static void logo(Renderer r, int x, int y) {
        int[][] art = com.nexusclient.core.online.BadgeArt.nexus();
        for (int row = 0; row < art.length; row++) {
            int start = -1;
            for (int col = 0; col <= art[row].length; col++) {
                int argb = col < art[row].length ? art[row][col] : 0;
                // Draw runs of the same colour as one rectangle (fewer things to draw).
                if (start >= 0 && (col == art[row].length || argb != art[row][start])) {
                    if ((art[row][start] >>> 24) != 0) {
                        r.rect(x + start, y + row, x + col, y + row + 1, art[row][start]);
                    }
                    start = -1;
                }
                if (start < 0 && col < art[row].length) {
                    start = col;
                }
            }
        }
    }

    private static final String[] STAR = {
            "...X...",
            "...X...",
            "XXXXXXX",
            ".XXXXX.",
            "..XXX..",
            ".XX.XX.",
            ".X...X.",
    };

    /**
     * A small star (7 x 7), drawn from pixels – the "★" letter doesn't exist in Minecraft 1.8.9's
     * font, so this looks the same on every version.
     */
    public static void star(Renderer r, int x, int y, int colour) {
        for (int row = 0; row < STAR.length; row++) {
            for (int col = 0; col < STAR[row].length(); col++) {
                if (STAR[row].charAt(col) == 'X') {
                    r.rect(x + col, y + row, x + col + 1, y + row + 1, colour);
                }
            }
        }
    }

    /** A pill-shaped on/off switch, 22 x 12, with its left edge at x. */
    public static void pillToggle(Renderer r, int x, int y, boolean on, boolean hovered) {
        roundBox(r, x, y, x + 22, y + 12, 6, on ? accent(0xFF) : 0xFF2A2840, on ? accent(0xFF) : (hovered ? TEXT_DIM : BORDER));
        int knob = on ? x + 12 : x + 2;
        round(r, knob, y + 2, knob + 8, y + 10, 4, on ? 0xFFFFFFFF : TEXT_DIM);
    }

    /** A modern slider: thin rounded track, filled part in the accent, and a round knob. */
    public static void smoothSlider(Renderer r, int x1, int y, int x2, double fraction, boolean active) {
        round(r, x1, y + 4, x2, y + 7, 1, 0xFF2A2840);
        int filled = x1 + (int) Math.round((x2 - x1) * Math.max(0, Math.min(1, fraction)));
        round(r, x1, y + 4, filled, y + 7, 1, accent());
        round(r, filled - 4, y + 1, filled + 4, y + 10, 4, active ? 0xFFFFFFFF : 0xFFE8E8F2);
        if (active) {
            r.outline(filled - 5, y, filled + 5, y + 11, accent(0x80));
        }
    }

    public static void centered(Renderer r, String text, int centerX, int y, int colour, boolean shadow) {
        r.text(text, centerX - r.textWidth(text) / 2, y, colour, shadow);
    }

    /** Cuts text to fit a width, adding "…". */
    public static String fit(Renderer r, String text, int maxWidth) {
        if (r.textWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && r.textWidth(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
