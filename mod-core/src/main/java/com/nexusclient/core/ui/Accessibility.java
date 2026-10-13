package com.nexusclient.core.ui;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.NexusSettings;

/**
 * Accessibility – the in-game side of the launcher's accessibility settings (the project brief:
 * "Accessibility (launcher and in-game): colourblind modes, bigger text, reduced motion").
 *
 *   - Colourblind mode: colours that MEAN something (good / so-so / bad, e.g. the frame-time graph,
 *     low durability, online dots, "blocked") come from good(), warn() and bad() instead of green /
 *     amber / red. The colours match the launcher's colourblind-*.css files:
 *       Red-green (protanopia / deuteranopia): blue / yellow / orange
 *       Blue-yellow (tritanopia):              pink / white / red
 *   - Reduced motion: menus and pop-ups appear without sliding, and long text doesn't scroll.
 *
 * Both live in Nexus Settings (Right Shift → Utility → Nexus Settings). The launcher switches them on
 * when they're on there (-Dnexus.colourblind, -Dnexus.reducedMotion).
 */
public final class Accessibility {

    private static final int[] NORMAL = {0xFF3DDC97, 0xFFFFB547, 0xFFFF5E5E};
    private static final int[] RED_GREEN = {0xFF4DA3FF, 0xFFFFD166, 0xFFFF8A00};
    private static final int[] BLUE_YELLOW = {0xFFFF4FA3, 0xFFE4E4EE, 0xFFFF3B30};

    private Accessibility() {}

    /** A colour for "good" (smooth frame, online, low ping). */
    public static int good() {
        return palette()[0];
    }

    /** A colour for "so-so" (a slow frame, away). */
    public static int warn() {
        return palette()[1];
    }

    /** A colour for "bad" (a very slow frame, almost broken, blocked, delete). */
    public static int bad() {
        return palette()[2];
    }

    /** True when animations should be skipped (things just appear). */
    public static boolean reducedMotion() {
        NexusSettings settings = settings();
        return settings != null && settings.reducedMotion.isOn();
    }

    private static int[] palette() {
        NexusSettings settings = settings();
        if (settings == null) {
            return NORMAL;
        }
        if (settings.colourblind.is("Red-green")) {
            return RED_GREEN;
        }
        if (settings.colourblind.is("Blue-yellow")) {
            return BLUE_YELLOW;
        }
        return NORMAL;
    }

    private static NexusSettings settings() {
        NexusCore core = NexusCore.get();
        return core == null ? null : core.get(NexusSettings.class);
    }

    /**
     * Applies what the launcher passed (called once at start). Only switches things ON – if the
     * launcher's setting is off, the player's in-game choice stays.
     */
    public static void applyLauncherSettings(NexusSettings settings) {
        String mode = System.getProperty("nexus.colourblind", "");
        if (mode.equalsIgnoreCase("RED_GREEN")) {
            settings.colourblind.set("Red-green");
        } else if (mode.equalsIgnoreCase("BLUE_YELLOW")) {
            settings.colourblind.set("Blue-yellow");
        }
        if (Boolean.getBoolean("nexus.reducedMotion")) {
            settings.reducedMotion.set(true);
        }
    }
}
