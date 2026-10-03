package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.Minecraft;

/** ZoomControl – works out how far zoomed in the camera is right now (used by FovMixin). */
public final class ZoomControl {

    private static long lastNanos = System.nanoTime();

    private ZoomControl() {}

    /** True while the zoom key is held in-game (not in a menu) and the Zoom mod is on. */
    public static boolean zooming() {
        return NexusModern.zoomKey != null && NexusModern.zoomKey.isDown()
                && Minecraft.getInstance().screen == null && NexusCore.get().get(Mods.Zoom.class).isActive();
    }

    /** How many times closer to show things (1 = normal). Glides smoothly if Smooth zoom is on. */
    public static double amount() {
        Mods.Zoom zoom = NexusCore.get().get(Mods.Zoom.class);
        double goal = zooming() ? zoom.target : 1;
        long now = System.nanoTime();
        double seconds = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (zoom.smooth.isOn()) {
            zoom.current += (goal - zoom.current) * Math.min(1, seconds * 14);
        } else {
            zoom.current = goal;
        }
        return Math.max(1, zoom.current);
    }

    /** Scrolling while zoomed: up = closer, down = further (between 1.5x and 50x). */
    public static void scroll(double amount) {
        Mods.Zoom zoom = NexusCore.get().get(Mods.Zoom.class);
        zoom.target = Math.max(1.5, Math.min(50, zoom.target * (amount > 0 ? 1.25 : 0.8)));
    }
}
