package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.LegacyDfr;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion Blur hook on 1.8.9: runs right before the HUD is drawn (the world is finished by then), so
 * only the world blurs. See LegacyDfr.blurWorld.
 */
@Mixin(GameRenderer.class)
public class MotionBlurMixin {

    @Inject(method = "render(FJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;render(F)V"))
    private void nexus$blurWorld(CallbackInfo ci) {
        LegacyDfr.blurWorld();
    }
}
