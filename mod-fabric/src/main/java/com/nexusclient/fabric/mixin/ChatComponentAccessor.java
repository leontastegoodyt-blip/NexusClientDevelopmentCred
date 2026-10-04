package com.nexusclient.fabric.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Lets Copy Chat read the chat's private parts: the lines on screen, scrolling and size. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    /** Every chat line, newest first (long messages are split over several lines). */
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> nexus$lines();

    /** How many lines the chat is scrolled up. */
    @Accessor("chatScrollbarPos")
    int nexus$scroll();

    @Invoker("getScale")
    double nexus$scale();

    @Invoker("getWidth")
    int nexus$width();
}
