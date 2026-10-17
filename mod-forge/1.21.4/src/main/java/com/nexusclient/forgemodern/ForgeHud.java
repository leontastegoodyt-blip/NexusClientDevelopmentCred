package com.nexusclient.forgemodern;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

/** ForgeHud (Forge 1.21.4) – draws the Nexus HUD as a Forge HUD layer, on top of the others. */
public final class ForgeHud {

    private ForgeHud() {}

    public static void register(IEventBus modBus, Consumer<GuiGraphics> draw) {
        modBus.addListener((AddGuiOverlayLayersEvent event) ->
                event.getLayeredDraw().add(id("hud"), (graphics, delta) -> draw.accept(graphics)));
    }

    /** "nexusclient:<path>" */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("nexusclient", path);
    }
}
