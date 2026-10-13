package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * FabricHooks (1.21.11) – the Fabric-only way to draw on top of the HUD. Kept apart from GameHooks
 * so the rest of the code doesn't need Fabric.
 */
public final class FabricHooks {

    private FabricHooks() {}

    /** Calls draw every frame, after Minecraft has drawn its own HUD. */
    public static void registerHud(Consumer<GuiGraphics> draw) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nexusclient", "hud"),
                (graphics, delta) -> draw.accept(graphics));
    }
}
