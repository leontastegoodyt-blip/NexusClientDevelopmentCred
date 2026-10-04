package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom nametags: shows your own name above your head in third person (F5), which Minecraft
 * normally hides. (The method gained an extra input in 1.21.2, so the entity is picked by type.)
 */
@Mixin(LivingEntityRenderer.class)
public class NametagMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void nexus$ownNametag(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) LivingEntity entity) {
        Mods.Nametags nametags = NexusCore.get().get(Mods.Nametags.class);
        Minecraft mc = Minecraft.getInstance();
        if (nametags.isActive() && nametags.ownNametag.isOn() && entity == mc.player && !mc.options.hideGui) {
            cir.setReturnValue(true);
        }
    }
}
