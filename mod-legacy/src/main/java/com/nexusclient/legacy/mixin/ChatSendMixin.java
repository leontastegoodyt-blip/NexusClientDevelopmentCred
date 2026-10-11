package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nexus party/clan chat on 1.8.9: when the player sends "@p hello" or "@c hello", Nexus Social sends
 * it to the party/clan and the normal send is cancelled, so nothing reaches the Minecraft server.
 */
@Mixin(Screen.class)
public class ChatSendMixin {

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void nexus$partyAndClanChat(String message, boolean toHud, CallbackInfo ci) {
        if (NexusCore.get().chatInput(message)) {
            ci.cancel();
        }
    }
}
