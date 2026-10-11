package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.LegacyDfr;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DFR and Motion Blur hooks on 1.8.9: just before and just after Minecraft swaps each finished frame
 * onto the screen (Display.update inside updateDisplay). See LegacyDfr.
 */
@Mixin(MinecraftClient.class)
public class DfrMixin {

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void nexus$beforeShow(CallbackInfo ci) {
        LegacyDfr.beforeShow();
    }

    @Inject(method = "updateDisplay", at = @At("TAIL"))
    private void nexus$afterShow(CallbackInfo ci) {
        LegacyDfr.afterShow();
    }
}
