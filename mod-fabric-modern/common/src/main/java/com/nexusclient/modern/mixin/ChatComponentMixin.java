package com.nexusclient.modern.mixin;

import com.nexusclient.modern.ChatFeatures;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Chat mods: every new chat message goes through ChatFeatures (timestamps, filter, mentions,
 * stacking, nick hider, auto GG) before it's added. (Longer Chat is in ChatLimitMixin, because
 * the 100-message limit moved in 1.20.5.)
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow
    public abstract void addMessage(Component message, MessageSignature signature, GuiMessageTag tag);

    @Shadow
    public abstract void rescaleChat();

    /** True while we're adding our changed copy of a message (so we don't change it twice). */
    @Unique
    private boolean nexus$adding;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void nexus$chat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
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
            addMessage(changed, signature, tag);
        } finally {
            nexus$adding = false;
        }
    }
}
