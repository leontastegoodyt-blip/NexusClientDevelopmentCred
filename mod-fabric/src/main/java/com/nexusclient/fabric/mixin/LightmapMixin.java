package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: lights the world as if you had Night Vision (only on your screen). */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void nexus$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (NexusCore.get().get(Mods.Fullbright.class).isActive()) {
            state.nightVisionEffectIntensity = 1.0F;
            state.needsUpdate = true;
        }
    }
}
