package com.nexusclient.fabric.mixin;

import com.nexusclient.fabric.DfrRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion Blur hook: runs right after Minecraft has drawn the world (and its post effects), before the
 * HUD is drawn on top – so only the world blurs. See DfrRenderer.blurWorld.
 */
@Mixin(GameRenderer.class)
public class MotionBlurMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER))
    private void nexus$blurWorld(CallbackInfo ci) {
        DfrRenderer.blurWorld();
    }
}
