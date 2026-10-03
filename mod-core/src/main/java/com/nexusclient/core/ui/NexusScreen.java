package com.nexusclient.core.ui;

import com.nexusclient.core.platform.Renderer;

/**
 * NexusScreen – a full-screen Nexus menu (the mod menu or the HUD editor), written once in mod-core.
 * The loader module wraps it in a real Minecraft screen and passes the mouse and keyboard through.
 *
 * Mouse coordinates are in GUI pixels. Buttons: 0 = left, 1 = right, 2 = middle.
 */
public interface NexusScreen {

    void render(Renderer renderer, int mouseX, int mouseY);

    boolean mouseClicked(double x, double y, int button);

    boolean mouseReleased(double x, double y, int button);

    boolean mouseDragged(double x, double y, int button);

    boolean mouseScrolled(double x, double y, double amount);

    /** A key was pressed (GLFW key number – see Keys). Return true if it was used. */
    boolean keyPressed(int key);

    /** A character was typed (for text boxes). */
    boolean charTyped(char character);

    /** Called when the screen closes (save settings here). */
    void onClose();
}
