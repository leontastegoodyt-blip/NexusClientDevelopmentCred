package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.Minecraft;

/**
 * MotionBlurControl – switches Minecraft's full-screen "post effect" for Motion Blur on and off.
 *
 * On 1.20.x the blur is a shader shipped with Nexus (assets/minecraft/shaders/post/
 * nexus_motion_blur_<strength>.json): each frame is mixed with the picture shown before. There's one
 * effect file per 10 % of strength, and this picks the closest one when the setting changes.
 * Versions without it (VersionHooks.MOTION_BLUR_SUPPORTED = false, 1.21.x) blur in ModernDfr instead,
 * by mixing the whole picture just before it's shown.
 */
public final class MotionBlurControl {

    private static int loaded = -1; // the strength now on screen, or -1 = none

    private MotionBlurControl() {}

    public static void tick(Minecraft mc) {
        if (!VersionHooks.MOTION_BLUR_SUPPORTED) {
            return;
        }
        Mods.MotionBlur blur = NexusCore.get().get(Mods.MotionBlur.class);
        int wanted = blur.isActive() && mc.level != null
                ? (int) Math.max(10, Math.min(90, Math.round(blur.strength.get() / 10.0) * 10)) : -1;
        if (wanted == loaded) {
            return;
        }
        VersionHooks.setMotionBlur(wanted);
        loaded = wanted;
    }
}
