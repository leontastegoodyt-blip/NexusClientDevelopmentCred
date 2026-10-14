package com.nexusclient.fabric;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Compat – the few small game calls that changed between 26.1, 26.2 and 26.3. This is the 26.3
 * version; the older 26.x builds (mod-fabric/<version>/src) have their own Compat.java with the
 * same methods, so the rest of the code stays the same everywhere.
 */
public final class Compat {

    private Compat() {}

    /** True when the game fills the whole screen (real fullscreen, or a window as big as the screen). */
    public static boolean fullscreen(Window window) {
        return window.isExclusiveFullscreen()
                || (window.getWidth() >= window.getScreenWidth() && window.getHeight() >= window.getScreenHeight());
    }

    /** Turns the drawing 90° around the X axis (used to lay dropped items flat). */
    public static void rotateX90(PoseStack poseStack) {
        poseStack.rotateDegrees(Axis.XP, 90);
    }

    /** The open screen (menu), or null while playing. */
    public static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    /** The chat box. */
    public static ChatComponent chat(Minecraft mc) {
        return mc.gui.hud.getChat();
    }

    /** Shows a short line just above the hotbar. */
    public static void overlayMessage(Minecraft mc, Component message) {
        mc.gui.hud.setOverlayMessage(message, false);
    }

    /** The boss bars at the top of the screen. */
    public static BossHealthOverlay bossOverlay(Minecraft mc) {
        return mc.gui.hud.getBossOverlay();
    }

    /** True while the HUD is hidden (F1). */
    public static boolean hudHidden(Minecraft mc) {
        return mc.gui.hud.isHidden();
    }

    /** The scoreboard slot for this team's own sidebar ("sidebar.team.red" …), or null if it has no colour. */
    public static DisplaySlot teamSidebar(PlayerTeam team) {
        if (team.getColor().isEmpty()) {
            return null;
        }
        String name = "sidebar.team." + team.getColor().get().getSerializedName();
        for (DisplaySlot slot : DisplaySlot.values()) {
            if (slot.getSerializedName().equals(name)) {
                return slot;
            }
        }
        return null;
    }

    /** Saves a screenshot, like pressing F2 (used by the self-test). */
    public static void screenshot(Minecraft mc) {
        net.minecraft.client.Screenshot.grab(mc, false);
    }
}
