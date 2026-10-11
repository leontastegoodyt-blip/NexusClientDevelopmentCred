package com.nexusclient.modern.mixin;

import com.mojang.blaze3d.platform.Window;
import com.nexusclient.modern.ModernDfr;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DFR (and Motion Blur on 1.21.x) hooks: just before and just after Minecraft swaps each finished
 * frame onto the screen. See ModernDfr for what happens there.
 */
@Mixin(Window.class)
public class DfrWindowMixin {

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void nexus$beforeShow(CallbackInfo ci) {
        ModernDfr.beforeShow();
    }

    @Inject(method = "updateDisplay", at = @At("TAIL"))
    private void nexus$afterShow(CallbackInfo ci) {
        ModernDfr.afterShow();
    }
}
