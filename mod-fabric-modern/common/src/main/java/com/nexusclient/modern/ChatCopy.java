package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.modern.mixin.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Copy Chat: right-clicking a chat message (with chat open) copies the whole message.
 *
 * Works out which line is under the mouse the same way Minecraft lays the chat out: lines stack
 * upwards from 40 pixels above the bottom of the screen, each one "line height" tall, scaled by the
 * chat scale setting, and shifted by how far the chat is scrolled.
 */
public final class ChatCopy {

    private ChatCopy() {}

    /** Copies the message at this mouse position. True if something was copied. */
    public static boolean copyAt(double mouseX, double mouseY) {
        if (!NexusCore.get().get(Mods.CopyChat.class).isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor access = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = access.nexus$lines();

        double scale = access.nexus$scale();
        int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
        double chatX = mouseX / scale - 4.0;
        double chatY = (mc.getWindow().getGuiScaledHeight() - mouseY - 40.0) / (scale * lineHeight);
        int visible = Math.min(chat.getLinesPerPage(), lines.size());
        if (chatX < -4.0 || chatX > Math.floor(access.nexus$width() / scale) || chatY < 0 || chatY >= visible) {
            return false; // not over a chat line
        }
        int index = (int) Math.floor(chatY) + access.nexus$scroll();
        if (index < 0 || index >= lines.size()) {
            return false;
        }

        String message = wholeMessage(lines, index);
        mc.keyboardHandler.setClipboard(message);
        mc.gui.setOverlayMessage(Component.literal("Copied chat message"), false);
        return true;
    }

    /**
     * A long message is split over several lines. Lines are stored newest first, and the bottom
     * line of each message is marked "endOfEntry". This collects every line of the clicked message.
     */
    private static String wholeMessage(List<GuiMessage.Line> lines, int index) {
        int bottom = index;
        while (bottom > 0 && !lines.get(bottom).endOfEntry()) {
            bottom--; // walk down to the message's last line
        }
        StringBuilder text = new StringBuilder();
        for (int i = bottom; i < lines.size() && (i == bottom || !lines.get(i).endOfEntry()); i++) {
            text.insert(0, plain(lines.get(i).content())); // higher index = earlier line
        }
        return text.toString();
    }

    /** The plain letters of one line, without colours. */
    static String plain(FormattedCharSequence line) {
        StringBuilder text = new StringBuilder();
        line.accept((position, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }
}
