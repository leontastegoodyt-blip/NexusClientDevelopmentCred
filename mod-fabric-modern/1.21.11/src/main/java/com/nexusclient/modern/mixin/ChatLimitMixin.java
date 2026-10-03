package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Longer Chat: raises Minecraft's 100-message chat history (since 1.20.5 it lives in two queue methods). */
@Mixin(ChatComponent.class)
public class ChatLimitMixin {

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
    private int nexus$longerChat(int limit) {
        Mods.LongerChat longer = NexusCore.get().get(Mods.LongerChat.class);
        return longer.isActive() ? longer.lines.intValue() : limit;
    }
}
