package com.nexusclient.core.util;

import java.awt.Color;

/**
 * Chroma – the neon rainbow effect: a colour that slowly cycles through every hue.
 * Things at different positions get slightly different colours, so text becomes a moving rainbow.
 */
public final class Chroma {

    /** How long one full trip around the rainbow takes, in milliseconds. */
    private static final double CYCLE_MS = 4000;

    private Chroma() {}

    /**
     * @param offset a position (e.g. x pixel) – shifts the colour along the rainbow
     * @param alpha  see-through-ness, 0 (invisible) to 255 (solid)
     * @return an ARGB colour
     */
    public static int color(int offset, int alpha) {
        double hue = ((System.currentTimeMillis() / CYCLE_MS) + offset / 300.0) % 1.0;
        int rgb = Color.HSBtoRGB((float) hue, 0.75f, 1.0f) & 0xFFFFFF;
        return (alpha << 24) | rgb;
    }
}
