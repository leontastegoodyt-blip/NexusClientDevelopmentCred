package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.legacy.ChatFeatures;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat mods: every new chat message goes through ChatFeatures (timestamps, filter, mentions,
 * stacking, nick hider, auto GG) before it's added. Also raises the 100-message history limit
 * for Longer Chat.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow
    public abstract void addMessage(Text message, int messageId);

    /** True while we're adding our changed copy of a message (so we don't change it twice). */
    @Unique
    private boolean nexus$adding;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;I)V", at = @At("HEAD"), cancellable = true)
    private void nexus$chat(Text message, int messageId, CallbackInfo ci) {
        if (nexus$adding) {
            return;
        }
        ChatFeatures.Result changed = ChatFeatures.process(message, messageId);
        ci.cancel();
        if (changed == null) {
            return; // hidden by the chat filter
        }
        nexus$adding = true;
        try {
            addMessage(changed.text, changed.id);
        } finally {
            nexus$adding = false;
        }
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int nexus$longerChat(int limit) {
        Mods.LongerChat longer = NexusCore.get().get(Mods.LongerChat.class);
        return longer.isActive() ? longer.lines.intValue() : limit;
    }
}
