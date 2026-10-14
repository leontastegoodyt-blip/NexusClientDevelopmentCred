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
 * Compat for 26.1 – the same methods as the 26.3 Compat (mod-fabric/src), written with the game
 * calls 26.1 has: the open screen is still Minecraft.screen, and the HUD is still Gui (26.2 split
 * it into Hud).
 */
public final class Compat {

    private Compat() {}

    /** True when the game fills the whole screen (real fullscreen, or a window as big as the screen). */
    public static boolean fullscreen(Window window) {
        return window.isFullscreen()
                || (window.getWidth() >= window.getScreenWidth() && window.getHeight() >= window.getScreenHeight());
    }

    /** Turns the drawing 90° around the X axis (used to lay dropped items flat). */
    public static void rotateX90(PoseStack poseStack) {
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
    }

    /** The open screen (menu), or null while playing. */
    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    /** The chat box. */
    public static ChatComponent chat(Minecraft mc) {
        return mc.gui.getChat();
    }

    /** Shows a short line just above the hotbar. */
    public static void overlayMessage(Minecraft mc, Component message) {
        mc.gui.setOverlayMessage(message, false);
    }

    /** The boss bars at the top of the screen. */
    public static BossHealthOverlay bossOverlay(Minecraft mc) {
        return mc.gui.getBossOverlay();
    }

    /** True while the HUD is hidden (F1). */
    public static boolean hudHidden(Minecraft mc) {
        return mc.options.hideGui;
    }

    /** The scoreboard slot for this team's own sidebar ("sidebar.team.red" …), or null if it has no colour. */
    public static DisplaySlot teamSidebar(PlayerTeam team) {
        return DisplaySlot.teamColorToSlot(team.getColor());
    }

    /** Saves a screenshot, like pressing F2 (used by the self-test). */
    public static void screenshot(Minecraft mc) {
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                message -> mc.execute(() -> mc.gui.getChat().addClientSystemMessage(message)));
    }
}
