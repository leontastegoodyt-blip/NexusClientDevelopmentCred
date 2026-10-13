package com.nexusclient.modern.mixin;

import com.nexusclient.modern.ModernGame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom crosshair: hides Minecraft's crosshair while Nexus draws its own (see ModernGame.drawHud).
 * (The method's inputs differ between versions, so this hook doesn't ask for them.)
 */
@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nexus$hideCrosshair(CallbackInfo ci) {
        if (ModernGame.customCrosshair(Minecraft.getInstance())) {
            ci.cancel();
        }
    }
}
