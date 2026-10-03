package com.nexusclient.modern;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * GameHooks for Minecraft 1.21.11 – key categories, the HUD and drawing changed in 1.21.6–1.21.9,
 * so this replaces the "classic" GameHooks the 1.20–1.21.4 builds use.
 */
public final class GameHooks {

    /** The "Nexus Client" key category (its English name is in assets/nexusclient/lang/en_us.json). */
    private static KeyMapping.Category category;

    private GameHooks() {}

    /** Adds a key to Minecraft's Controls menu, under "Nexus Client". */
    public static KeyMapping registerKey(String name, int defaultKey) {
        if (category == null) {
            category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nexusclient", "main"));
        }
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, defaultKey, category));
    }

    /** Calls draw every frame, after Minecraft has drawn its own HUD. */
    public static void registerHud(Consumer<GuiGraphics> draw) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nexusclient", "hud"),
                (graphics, delta) -> draw.accept(graphics));
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
