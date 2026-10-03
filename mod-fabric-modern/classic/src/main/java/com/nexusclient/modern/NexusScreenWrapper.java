package com.nexusclient.modern;

import com.nexusclient.core.ui.NexusScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * NexusScreenWrapper – a real Minecraft screen that shows one of mod-core's menus (the mod menu or
 * HUD editor), passing the mouse and keyboard through to it. For Minecraft 1.20 – 1.21.8, which use
 * the same key and mouse-button numbers as mod-core, so nothing needs converting.
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
        // No vanilla background – the Nexus menus draw their own dark backdrop.
        screen.render(new FabricRenderer(graphics), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return screen.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return screen.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return screen.mouseDragged(x, y, button);
    }

    // Scrolling: 1.20–1.20.1 call the first version, 1.20.2+ the second (with sideways scrolling).
    // Each Minecraft version only uses the one it knows. (No @Override, because each one only
    // exists in some versions.)

    public boolean mouseScrolled(double x, double y, double amount) {
        return screen.mouseScrolled(x, y, amount);
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return screen.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // The Nexus screens handle Esc themselves (to close text boxes first, save, etc.).
        return screen.keyPressed(key);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return screen.charTyped(character);
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
