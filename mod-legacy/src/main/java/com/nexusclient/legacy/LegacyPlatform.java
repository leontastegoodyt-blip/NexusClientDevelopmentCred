package com.nexusclient.legacy;

import com.nexusclient.core.platform.Platform;
import com.nexusclient.core.ui.NexusScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

/** LegacyPlatform – opens Nexus screens, uses the clipboard and sends chat, in Minecraft 1.8.9. */
public class LegacyPlatform implements Platform {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void openScreen(NexusScreen screen) {
        mc.setScreen(new NexusScreenWrapper(screen));
    }

    @Override
    public void closeScreen() {
        mc.setScreen(null);
    }

    @Override
    public void copyToClipboard(String text) {
        Screen.setClipboard(text);
    }

    @Override
    public String clipboard() {
        return Screen.getClipboard();
    }

    @Override
    public void sendChat(String message) {
        if (mc.player != null) {
            mc.player.sendChatMessage(message);
        }
    }

    @Override
    public void showNotice(String message) {
        // "[Nexus] message" in chat, only on this player's screen (never sent to the server).
        // 1.8.9 chat only has the 16 classic colours, so cyan = AQUA.
        if (mc.inGameHud == null) {
            return;
        }
        mc.inGameHud.getChatHud().addMessage(new LiteralText("[Nexus] ")
                .setStyle(new Style().setFormatting(Formatting.AQUA))
                .append(new LiteralText(message).setStyle(new Style().setFormatting(Formatting.GRAY))));
    }
}
