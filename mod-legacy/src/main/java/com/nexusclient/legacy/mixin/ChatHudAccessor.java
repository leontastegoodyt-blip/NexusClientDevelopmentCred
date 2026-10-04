package com.nexusclient.legacy.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lets Copy Chat read the chat lines on screen and how far the chat is scrolled (1.8.9). */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {

    /** The chat lines as shown (long messages split over several lines), newest first. */
    @Accessor("visibleMessages")
    List<ChatHudLine> nexus$lines();

    @Accessor("scrolledLines")
    int nexus$scroll();
}
