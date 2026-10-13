package com.nexusclient.modern;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

/**
 * GameHooks – things that work the same on Minecraft 1.20 up to 1.21.8, but changed in 1.21.9+
 * (the 1.21.11 folder has its own GameHooks). This "classic" folder is shared by the 1.20.1,
 * 1.20.4, 1.20.6 and 1.21.4 builds.
 */
public final class GameHooks {

    /** Key category name (its English text is in assets/nexusclient/lang/en_us.json). */
    private static final String CATEGORY = "key.categories.nexusclient";

    private GameHooks() {}

    /** A key for Minecraft's Controls menu, under "Nexus Client" (the mod loader registers it). */
    public static KeyMapping newKey(String name, int defaultKey) {
        return new KeyMapping(name, defaultKey, CATEGORY);
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
