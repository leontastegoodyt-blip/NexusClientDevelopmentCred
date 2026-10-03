package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Longer Chat: raises Minecraft's 100-message chat history (1.20–1.20.4 keep it in addMessage). */
@Mixin(ChatComponent.class)
public class ChatLimitMixin {

    @ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "ILnet/minecraft/client/GuiMessageTag;Z)V", constant = @Constant(intValue = 100))
    private int nexus$longerChat(int limit) {
        Mods.LongerChat longer = NexusCore.get().get(Mods.LongerChat.class);
        return longer.isActive() ? longer.lines.intValue() : limit;
    }
}
