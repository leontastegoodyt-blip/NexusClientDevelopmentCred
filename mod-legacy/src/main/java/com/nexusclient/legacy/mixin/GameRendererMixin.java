package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.legacy.ZoomControl;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera and lighting mods:
 *  - Zoom: a smaller field of view (FOV) makes everything look closer
 *  - No Hurt-Cam Shake: skips the screen tilt when you take damage
 *  - Fullbright: lights the world as if brightness were turned far past "Bright" (only on your screen)
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nexus$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        double zoom = ZoomControl.amount();
        if (zoom > 1.001) {
            cir.setReturnValue((float) (cir.getReturnValue() / zoom));
        }
    }

    @Inject(method = "bobViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void nexus$noHurtCam(float tickDelta, CallbackInfo ci) {
        if (NexusCore.get().get(Mods.NoHurtCam.class).isActive()) {
            ci.cancel();
        }
    }

    @Redirect(method = "updateLightmap", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;gamma:F", opcode = Opcodes.GETFIELD))
    private float nexus$fullbright(GameOptions options) {
        return NexusCore.get().get(Mods.Fullbright.class).isActive() ? 16.0F : options.gamma;
    }
}
