package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.settings.SettingsStore;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

/**
 * DisplaySetup – makes the launcher look sharp and the right size on any screen. It runs once,
 * BEFORE JavaFX starts (JavaFX reads these choices only when it starts):
 *
 *   - Interface size: the launcher is designed for a 1080p screen. On bigger screens (1440p, 4K)
 *     with Windows scaling left at 100%, everything would look tiny, so Nexus draws the whole
 *     interface bigger – properly, at full resolution, not stretched or blurry. E.g. 1440p → 133%.
 *     If the player set their own Windows scaling, Nexus follows it. They can also pick a size in
 *     Settings → Appearance (needs a restart).
 *   - Smoothness: animations run at the screen's refresh rate (at least 60 per second), so a
 *     144 Hz screen gets 144 Hz animations.
 */
public final class DisplaySetup {

    /** The interface size choices in Settings → Appearance (0 = automatic). */
    public static final int[] SIZE_CHOICES = {0, 100, 125, 133, 150, 175, 200};

    /** The scale picked for this run (1.0 = 100%), for the settings page to show. */
    private static double appliedScale = 1.0;
    private static double automaticScale = 1.0;

    private DisplaySetup() {}

    /** Call once at the very start, before JavaFX starts. */
    public static void apply() {
        GraphicsDevice screen;
        try {
            screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        } catch (Throwable noScreenInfo) {
            return; // e.g. no graphics available – JavaFX's own defaults will do
        }
        DisplayMode mode = screen.getDisplayMode();
        // The scaling the player picked in Windows (1.0 = 100%, 1.25 = 125%…).
        double systemScale = screen.getDefaultConfiguration().getDefaultTransform().getScaleY();

        automaticScale = systemScale;
        if (systemScale <= 1.0 && mode.getHeight() >= 1440) {
            // Scaling left at 100% on a big screen: size things as if on a 1080p screen.
            automaticScale = mode.getHeight() / 1080.0;
        }
        int chosen = SettingsStore.get().uiScalePercent;
        appliedScale = chosen > 0 ? chosen / 100.0 : automaticScale;
        if (Math.abs(appliedScale - systemScale) > 0.01) {
            String percent = String.format(Locale.ROOT, "%.0f%%", appliedScale * 100);
            System.setProperty("glass.win.uiScale", percent);   // Windows
            System.setProperty("glass.gtk.uiScale", percent);   // Linux (Macs scale by themselves)
        }

        // Animate at the screen's refresh rate (never below 60 times a second).
        int refresh = mode.getRefreshRate();
        if (refresh > 60) {
            System.setProperty("javafx.animation.pulse", String.valueOf(refresh));
        }
    }

    /** The interface size in use right now, as a percentage (e.g. 133). */
    public static int appliedPercent() {
        return (int) Math.round(appliedScale * 100);
    }

    /** What "Automatic" picks on this screen, as a percentage. */
    public static int automaticPercent() {
        return (int) Math.round(automaticScale * 100);
    }
}
