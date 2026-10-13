package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.NexusCosmeticsFeature;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Nexus cosmetics feature (Halo, Neon Wings) to both player renderers (normal and slim arms). */
@Mixin(PlayerEntityRenderer.class)
public class CosmeticFeatureMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
    private void nexus$addCosmetics(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) this).nexus$addFeature(new NexusCosmeticsFeature((PlayerEntityRenderer) (Object) this));
    }
}
