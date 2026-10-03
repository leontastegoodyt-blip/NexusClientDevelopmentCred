package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * GameHooks – things that work the same on Minecraft 1.20 up to 1.21.8, but changed in 1.21.9+
 * (the 1.21.11 folder has its own GameHooks). This "classic" folder is shared by the 1.20.1,
 * 1.20.4, 1.20.6 and 1.21.4 builds.
 */
public final class GameHooks {

    /** Key category name (its English text is in assets/nexusclient/lang/en_us.json). */
    private static final String CATEGORY = "key.categories.nexusclient";

    private GameHooks() {}

    /** Adds a key to Minecraft's Controls menu, under "Nexus Client". */
    public static KeyMapping registerKey(String name, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, defaultKey, CATEGORY));
    }

    /** Calls draw every frame, after Minecraft has drawn its own HUD. */
    public static void registerHud(Consumer<GuiGraphics> draw) {
        HudRenderCallback.EVENT.register((graphics, delta) -> draw.accept(graphics));
    }

    /** Starts drawing moved to (x, y) and scaled (until pop). */
    public static void push(GuiGraphics graphics, float x, float y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
    }

    public static void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
