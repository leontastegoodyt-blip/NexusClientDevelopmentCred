package com.nexusclient.modern.mixin;

import com.nexusclient.modern.ChatCopy;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Copy Chat: right-click (button 1) a message while chat is open. For 1.20 – 1.21.8. */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nexus$copyChat(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 1 && ChatCopy.copyAt(x, y)) {
            cir.setReturnValue(true);
        }
    }
}
