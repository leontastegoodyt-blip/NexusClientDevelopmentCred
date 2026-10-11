package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Nexus party/clan chat: when the player sends "@p hello" or "@c hello", Nexus Social sends it to the
 * party/clan instead, and the message is emptied here – Minecraft ignores empty chat, so nothing
 * reaches the Minecraft server.
 */
@Mixin(ChatScreen.class)
public class ChatSendMixin {

    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
    private String nexus$partyAndClanChat(String message) {
        return NexusCore.get().chatInput(message) ? "" : message;
    }
}
