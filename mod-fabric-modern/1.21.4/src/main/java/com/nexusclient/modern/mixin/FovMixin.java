package com.nexusclient.modern.mixin;

import com.nexusclient.modern.ZoomControl;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: a smaller field of view (FOV) makes everything look closer. (1.21.2+: the FOV is a float.) */
@Mixin(GameRenderer.class)
public class FovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nexus$zoom(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Float> cir) {
        double zoom = ZoomControl.amount();
        if (zoom > 1.001) {
            cir.setReturnValue((float) (cir.getReturnValue() / zoom));
        }
    }
}
