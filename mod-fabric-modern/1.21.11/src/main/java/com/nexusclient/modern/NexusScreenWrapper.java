package com.nexusclient.modern;

import com.nexusclient.core.ui.NexusScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * NexusScreenWrapper for Minecraft 1.21.11 – a real Minecraft screen that shows one of mod-core's
 * menus (the mod menu or HUD editor). Since 1.21.9 keys and clicks arrive as "event" objects.
 * 1.21.11 still uses the same key and mouse-button numbers as mod-core (GLFW), so nothing needs
 * converting (unlike 26.x).
 */
public class NexusScreenWrapper extends Screen {

    private final NexusScreen screen;

    public NexusScreenWrapper(NexusScreen screen) {
        super(Component.literal("Nexus"));
        this.screen = screen;
    }

    /** The Nexus menu shown inside this screen. */
    public NexusScreen screen() {
        return screen;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        screen.render(new FabricRenderer(graphics), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // No blurry vanilla background – the Nexus menus draw their own dark backdrop.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return screen.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return screen.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return screen.mouseDragged(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return screen.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The Nexus screens handle Esc themselves (to close text boxes first, save, etc.).
        return screen.keyPressed(event.key());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        boolean used = false;
        for (char c : event.codepointAsString().toCharArray()) {
            used |= screen.charTyped(c);
        }
        return used;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // see keyPressed
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the game keeps running behind the menu (like Lunar)
    }

    @Override
    public void removed() {
        screen.onClose();
    }
}
