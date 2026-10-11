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

    @Override
    public void showChatLine(String tag, int tagRgb, String text) {
        if (mc.inGameHud == null) {
            return;
        }
        mc.inGameHud.getChatHud().addMessage(new LiteralText(tag)
                .setStyle(new Style().setFormatting(nearest(tagRgb)))
                .append(new LiteralText(text).setStyle(new Style().setFormatting(Formatting.WHITE))));
    }

    /** 1.8.9 chat has only 16 colours: the one closest to an RGB colour. */
    private static Formatting nearest(int rgb) {
        Formatting[] names = {Formatting.DARK_BLUE, Formatting.DARK_GREEN, Formatting.DARK_AQUA, Formatting.DARK_RED,
                Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY, Formatting.DARK_GRAY, Formatting.BLUE,
                Formatting.GREEN, Formatting.AQUA, Formatting.RED, Formatting.LIGHT_PURPLE, Formatting.YELLOW,
                Formatting.WHITE};
        int[] colours = {0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
                0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        Formatting best = Formatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < colours.length; i++) {
            long dr = ((rgb >> 16) & 255) - ((colours[i] >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((colours[i] >> 8) & 255);
            long db = (rgb & 255) - (colours[i] & 255);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = names[i];
            }
        }
        return best;
    }
}
