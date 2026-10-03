package com.nexusclient.legacy;

import com.nexusclient.core.ui.NexusScreen;
import com.nexusclient.core.util.Keys;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * NexusScreenWrapper – a real Minecraft 1.8.9 screen that shows one of mod-core's menus (the mod
 * menu or HUD editor), passing the mouse and keyboard through to it.
 */
public class NexusScreenWrapper extends Screen {

    private final NexusScreen screen;

    public NexusScreenWrapper(NexusScreen screen) {
        this.screen = screen;
    }

    /** The Nexus menu shown inside this screen. */
    public NexusScreen screen() {
        return screen;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        // No vanilla background – the Nexus menus draw their own dark backdrop.
        screen.render(new LegacyRenderer(), mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        screen.mouseClicked(mouseX, mouseY, button); // 1.8.9 already uses 0 = left, 1 = right, 2 = middle
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        screen.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceClick) {
        screen.mouseDragged(mouseX, mouseY, button);
    }

    @Override
    public void handleMouse() {
        super.handleMouse(); // clicks, releases and drags (see above)
        int wheel = Mouse.getEventDWheel(); // 1.8.9 has no scroll method on screens, so read it here
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / client.width;
            int y = height - Mouse.getEventY() * height / client.height - 1;
            screen.mouseScrolled(x, y, wheel > 0 ? 1 : -1);
        }
    }

    @Override
    protected void keyPressed(char character, int code) {
        // The Nexus screens handle Esc themselves (to close text boxes first, save, etc.).
        // 1.8.9 sends the key and the typed letter together; mod-core wants them separately.
        int key = toCoreKey(code);
        if (key != -1) {
            screen.keyPressed(key);
        }
        if (character >= ' ' && character != 127) {
            screen.charTyped(character);
        }
    }

    /**
     * 1.8.9 numbers keys the LWJGL 2 way (Right Shift = 54), but mod-core's menus use the GLFW
     * numbers newer versions use (Right Shift = 344, see core/util/Keys). This converts.
     */
    static int toCoreKey(int code) {
        switch (code) {
            case Keyboard.KEY_ESCAPE: return Keys.ESCAPE;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER: return Keys.ENTER;
            case Keyboard.KEY_TAB: return Keys.TAB;
            case Keyboard.KEY_BACK: return Keys.BACKSPACE;
            case Keyboard.KEY_DELETE: return Keys.DELETE;
            case Keyboard.KEY_RIGHT: return Keys.RIGHT;
            case Keyboard.KEY_LEFT: return Keys.LEFT;
            case Keyboard.KEY_DOWN: return Keys.DOWN;
            case Keyboard.KEY_UP: return Keys.UP;
            case Keyboard.KEY_RSHIFT: return Keys.RIGHT_SHIFT;
            case Keyboard.KEY_LCONTROL: return Keys.LEFT_CONTROL;
            case Keyboard.KEY_V: return Keys.V;
            case Keyboard.KEY_C: return Keys.C;
            default: return -1; // a key the menus don't use
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false; // the game keeps running behind the menu (like Lunar)
    }

    @Override
    public void removed() {
        screen.onClose();
    }
}
