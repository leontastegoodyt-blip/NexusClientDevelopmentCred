package com.nexusclient.fabric.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nexusclient.fabric.DfrRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DFR hooks for 26.1: just before and just after Minecraft shows each finished frame
 * (RenderSystem.flipFrame – 26.2+ show it through a "GpuSurface" instead). See DfrRenderer.
 */
@Mixin(RenderSystem.class)
public class DfrMixin {

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void nexus$beforeShow(CallbackInfo ci) {
        DfrRenderer.beforeShow();
    }

    @Inject(method = "flipFrame", at = @At("TAIL"))
    private static void nexus$afterShow(CallbackInfo ci) {
        DfrRenderer.afterShow();
    }
}
