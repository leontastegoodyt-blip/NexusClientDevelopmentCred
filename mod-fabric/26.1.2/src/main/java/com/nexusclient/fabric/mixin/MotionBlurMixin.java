package com.nexusclient.fabric.mixin;

import com.nexusclient.fabric.DfrRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion Blur hook for 26.1 – 26.2: runs right after Minecraft has drawn the world, before the HUD
 * is drawn on top – so only the world blurs. (26.3 hooks after applyPostEffects, which these
 * versions don't have.) See DfrRenderer.blurWorld.
 */
@Mixin(GameRenderer.class)
public class MotionBlurMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void nexus$blurWorld(CallbackInfo ci) {
        DfrRenderer.blurWorld();
    }
}
