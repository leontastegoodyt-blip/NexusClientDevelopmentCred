package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.fabric.ChatFeatures;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Chat mods: every new chat message goes through ChatFeatures (timestamps, filter, mentions,
 * stacking, nick hider, auto GG) before it's added. Also raises the 100-message history limit
 * for Longer Chat.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow
    private void addMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        throw new AssertionError(); // replaced by Minecraft's real method
    }

    @Shadow
    public abstract void rescaleChat();

    /** True while we're adding our changed copy of a message (so we don't change it twice). */
    @Unique
    private boolean nexus$adding;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void nexus$chat(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag,
                            CallbackInfo ci) {
        if (nexus$adding) {
            return;
        }
        Component changed = ChatFeatures.process(message);
        ci.cancel();
        if (changed == null) {
            return; // hidden by the chat filter
        }
        if (ChatFeatures.isRepeat() && !allMessages.isEmpty()) {
            allMessages.remove(0); // replace "hi (x2)" with "hi (x3)" instead of adding another line
            rescaleChat();
        }
        nexus$adding = true;
        try {
            addMessage(changed, signature, source, tag);
        } finally {
            nexus$adding = false;
        }
    }

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int nexus$longerChat(int limit) {
        Mods.LongerChat longer = NexusCore.get().get(Mods.LongerChat.class);
        return longer.isActive() ? longer.lines.intValue() : limit;
    }
}
