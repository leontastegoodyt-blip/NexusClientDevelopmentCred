package com.nexusclient.modern.mixin;

import com.nexusclient.modern.NexusCosmeticsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Nexus cosmetics layer (Halo, Neon Wings) to every player renderer (1.20 – 1.21.4). */
@Mixin(PlayerRenderer.class)
public class CosmeticLayerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nexus$addCosmetics(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) this).nexus$addLayer(new NexusCosmeticsLayer((PlayerRenderer) (Object) this));
    }
}
