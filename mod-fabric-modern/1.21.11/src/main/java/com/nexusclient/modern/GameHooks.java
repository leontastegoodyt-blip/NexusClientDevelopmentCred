package com.nexusclient.modern;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * GameHooks for Minecraft 1.21.11 – key categories, the HUD and drawing changed in 1.21.6–1.21.9,
 * so this replaces the "classic" GameHooks the 1.20–1.21.4 builds use.
 */
public final class GameHooks {

    /** The "Nexus Client" key category (its English name is in assets/nexusclient/lang/en_us.json). */
    private static KeyMapping.Category category;

    private GameHooks() {}

    /** A key for Minecraft's Controls menu, under "Nexus Client" (the mod loader registers it). */
    public static KeyMapping newKey(String name, int defaultKey) {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nexusclient", "main"));
        }
        return new KeyMapping(name, defaultKey, category);
    }

    /** Starts drawing moved to (x, y) and scaled (until pop). 1.21.6+ uses a 2D matrix here. */
    public static void push(GuiGraphics graphics, float x, float y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
    }

    public static void pop(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }
}
