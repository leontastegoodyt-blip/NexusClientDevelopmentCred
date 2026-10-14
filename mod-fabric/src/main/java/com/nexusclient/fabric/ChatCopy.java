package com.nexusclient.fabric;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.fabric.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Copy Chat (26.x): right-clicking a chat message (with chat open) copies the whole message.
 * Works out which line is under the mouse the same way Minecraft lays the chat out: lines stack
 * upwards from 40 pixels above the bottom of the screen, scaled by the chat scale and shifted by
 * scrolling. In 26.x every line knows the whole message it belongs to ("parent").
 */
public final class ChatCopy {

    private ChatCopy() {}

    /** Copies the message at this mouse position. True if something was copied. */
    public static boolean copyAt(double mouseX, double mouseY) {
        if (!NexusCore.get().get(Mods.CopyChat.class).isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = com.nexusclient.fabric.Compat.chat(mc);
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
        mc.keyboardHandler.setClipboard(lines.get(index).parent().content().getString());
        com.nexusclient.fabric.Compat.overlayMessage(mc, Component.literal("Copied chat message"));
        return true;
    }
}
