package com.nexusclient.core.settings;

import com.nexusclient.core.util.Chroma;

/**
 * A colour setting, stored as ARGB (alpha, red, green, blue – e.g. 0xFF3AA8FF is solid Nexus blue).
 * It can also be set to "chroma": a neon rainbow that slowly cycles through all colours.
 */
public class ColorSetting extends Setting<Integer> {

    private boolean chroma;

    public ColorSetting(String id, String name, int defaultArgb) {
        super(id, name, defaultArgb);
    }

    /** The colour to draw with right now (the rainbow colour if chroma is on). */
    public int argb() {
        return chroma ? Chroma.color(0, (value >>> 24) & 0xFF) : value;
    }

    /** The chroma colour for something at a position (so a line of text becomes a moving rainbow). */
    public int argbAt(int offset) {
        return chroma ? Chroma.color(offset, (value >>> 24) & 0xFF) : value;
    }

    public boolean chroma() {
        return chroma;
    }

    public void setChroma(boolean chroma) {
        this.chroma = chroma;
    }

    @Override
    public String save() {
        return Integer.toHexString(value) + (chroma ? ":chroma" : "");
    }

    @Override
    public void load(String saved) {
        try {
            String[] parts = saved.split(":");
            value = (int) Long.parseLong(parts[0], 16);
            chroma = parts.length > 1 && parts[1].equals("chroma");
        } catch (RuntimeException ignored) {
            // keep the current value
        }
    }
}
