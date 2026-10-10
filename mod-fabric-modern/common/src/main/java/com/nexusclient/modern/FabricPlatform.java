package com.nexusclient.modern;

import com.nexusclient.core.platform.Platform;
import com.nexusclient.core.ui.NexusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** FabricPlatform – opens Nexus screens, uses the clipboard and sends chat, in Minecraft 1.20–1.21. */
public class FabricPlatform implements Platform {

    private final Minecraft mc = Minecraft.getInstance();

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
        mc.keyboardHandler.setClipboard(text);
    }

    @Override
    public String clipboard() {
        return mc.keyboardHandler.getClipboard();
    }

    @Override
    public void sendChat(String message) {
        if (mc.getConnection() != null) {
            mc.getConnection().sendChat(message);
        }
    }

    @Override
    public void showNotice(String message) {
        // "[Nexus] message" in chat, only on this player's screen (never sent to the server).
        mc.gui.getChat().addMessage(Component.literal("[Nexus] ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x3AA8FF)))
                .append(Component.literal(message).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC8C8D8)))));
    }
}
