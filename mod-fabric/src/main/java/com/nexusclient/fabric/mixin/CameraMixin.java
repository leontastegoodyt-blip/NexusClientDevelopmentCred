package com.nexusclient.fabric.mixin;

import com.nexusclient.fabric.ZoomControl;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: a smaller field of view (FOV) makes everything look closer. */
@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nexus$zoom(CallbackInfoReturnable<Float> cir) {
        double zoom = ZoomControl.amount();
        if (zoom > 1.001) {
            cir.setReturnValue((float) (cir.getReturnValue() / zoom));
        }
    }
}
