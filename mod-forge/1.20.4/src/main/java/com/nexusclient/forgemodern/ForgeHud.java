package com.nexusclient.forgemodern;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

/** ForgeHud (Forge 1.20.4) – draws the Nexus HUD as a Forge "GUI overlay", above all the others. */
public final class ForgeHud {

    private ForgeHud() {}

    public static void register(IEventBus modBus, Consumer<GuiGraphics> draw) {
        modBus.addListener((RegisterGuiOverlaysEvent event) ->
                event.registerAboveAll("hud", (gui, graphics, partialTick, width, height) -> draw.accept(graphics)));
    }

    /** "nexusclient:<path>" */
    public static ResourceLocation id(String path) {
        return new ResourceLocation("nexusclient", path);
    }
}
