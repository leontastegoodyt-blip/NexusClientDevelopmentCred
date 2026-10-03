package com.nexusclient.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.nexusclient.core.ui.NexusScreen;
import com.nexusclient.core.util.Keys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * NexusScreenWrapper – a real Minecraft screen that shows one of mod-core's menus (the mod menu or
 * HUD editor), passing the mouse and keyboard through to it.
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        screen.render(new FabricRenderer(graphics), mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // No blurry vanilla background – the Nexus menus draw their own dark backdrop.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return screen.mouseClicked(event.x(), event.y(), toCoreButton(event.button()));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return screen.mouseReleased(event.x(), event.y(), toCoreButton(event.button()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return screen.mouseDragged(event.x(), event.y(), toCoreButton(event.button()));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return screen.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The Nexus screens handle Esc themselves (to close text boxes first, save, etc.).
        return screen.keyPressed(toCoreKey(event.key()));
    }

    /** Minecraft 26.x counts mouse buttons from 1 (left = 1, right = 3); mod-core uses 0 = left, 1 = right, 2 = middle. */
    public static int toCoreButton(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button;
        };
    }

    /**
     * Minecraft 26.x numbers keys the SDL way (Right Shift = 229), but mod-core's menus use the
     * GLFW numbers every other version uses (Right Shift = 344, see core/util/Keys). This converts.
     */
    private static int toCoreKey(int key) {
        return switch (key) {
            case InputConstants.KEY_ESCAPE -> Keys.ESCAPE;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> Keys.ENTER;
            case InputConstants.KEY_TAB -> Keys.TAB;
            case InputConstants.KEY_BACKSPACE -> Keys.BACKSPACE;
            case InputConstants.KEY_DELETE -> Keys.DELETE;
            case InputConstants.KEY_RIGHT -> Keys.RIGHT;
            case InputConstants.KEY_LEFT -> Keys.LEFT;
            case InputConstants.KEY_DOWN -> Keys.DOWN;
            case InputConstants.KEY_UP -> Keys.UP;
            case InputConstants.KEY_RSHIFT -> Keys.RIGHT_SHIFT;
            case InputConstants.KEY_LCONTROL -> Keys.LEFT_CONTROL;
            case InputConstants.KEY_V -> Keys.V;
            case InputConstants.KEY_C -> Keys.C;
            default -> -1; // a key the menus don't use
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String typed = event.codepointAsString();
        boolean used = false;
        for (char c : typed.toCharArray()) {
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
