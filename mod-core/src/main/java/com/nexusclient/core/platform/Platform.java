package com.nexusclient.core.platform;

import com.nexusclient.core.ui.NexusScreen;

/**
 * Platform – things mod-core asks the loader module to do in the game: open a Nexus screen,
 * use the clipboard, send a chat message, show the player a notice.
 */
public interface Platform {

    /** Shows a Nexus screen (mod menu, HUD editor) using Minecraft's screen system. */
    void openScreen(NexusScreen screen);

    /** Closes the current Nexus screen and goes back to the game. */
    void closeScreen();

    void copyToClipboard(String text);

    String clipboard();

    /** Sends a chat message as the player (used by Auto GG). */
    void sendChat(String message);

    /** Shows a short Nexus message to the player only (in chat), e.g. which mods a server blocked. */
    void showNotice(String message);

    /**
     * Shows a chat line only on this player's screen with a coloured tag, e.g. "[Party] Steve: hi"
     * (Nexus party/clan chat – never sent to the Minecraft server).
     *
     * @param tagRgb the tag's colour (RGB)
     */
    default void showChatLine(String tag, int tagRgb, String text) {
        showNotice(tag + text);
    }
}
