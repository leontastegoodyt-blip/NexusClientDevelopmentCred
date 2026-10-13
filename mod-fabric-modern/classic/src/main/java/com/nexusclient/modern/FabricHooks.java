package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * FabricHooks (1.20 – 1.21.4) – the Fabric-only way to draw on top of the HUD. Kept apart from
 * GameHooks so the rest of the code doesn't need Fabric (1.21.11 has its own copy).
 */
public final class FabricHooks {

    private FabricHooks() {}

    /** Calls draw every frame, after Minecraft has drawn its own HUD. */
    public static void registerHud(Consumer<GuiGraphics> draw) {
        HudRenderCallback.EVENT.register((graphics, delta) -> draw.accept(graphics));
    }
}
