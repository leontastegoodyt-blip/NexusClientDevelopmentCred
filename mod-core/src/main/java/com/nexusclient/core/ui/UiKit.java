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
