package com.nexusclient.forgemodern;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

/**
 * ForgeHud (Forge 1.20.6) – Forge 1.20.6 has no event for drawing on the HUD (it dropped its own HUD
 * layers when Minecraft got its own), so a mixin (ForgeHudMixin) calls the drawing at the end of
 * Minecraft's HUD instead. This only remembers what to draw.
 */
public final class ForgeHud {

    /** What ForgeHudMixin draws every frame (nothing until the mod has started). */
    public static Consumer<GuiGraphics> draw = graphics -> {};

    private ForgeHud() {}

    public static void register(IEventBus modBus, Consumer<GuiGraphics> draw) {
        ForgeHud.draw = draw;
    }

    /** "nexusclient:<path>" */
    public static ResourceLocation id(String path) {
        return new ResourceLocation("nexusclient", path);
    }
}
