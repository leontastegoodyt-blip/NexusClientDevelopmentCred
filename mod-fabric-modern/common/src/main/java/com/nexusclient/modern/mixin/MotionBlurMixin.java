package com.nexusclient.modern.mixin;

import com.nexusclient.modern.ModernDfr;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion Blur hook: runs right after Minecraft has drawn the world (and entity outlines), before the
 * HUD is drawn on top – so only the world blurs. See ModernDfr.blurWorld.
 */
@Mixin(GameRenderer.class)
public class MotionBlurMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void nexus$blurWorld(CallbackInfo ci) {
        ModernDfr.blurWorld();
    }
}
