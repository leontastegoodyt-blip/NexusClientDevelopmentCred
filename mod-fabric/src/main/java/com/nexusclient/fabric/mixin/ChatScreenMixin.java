package com.nexusclient.fabric.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.nexusclient.fabric.ChatCopy;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Copy Chat: right-click a message while chat is open. (26.x numbers the right button 3.) */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nexus$copyChat(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && ChatCopy.copyAt(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }
}
