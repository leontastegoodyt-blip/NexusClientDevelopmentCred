package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright (with LightTextureMixin): Night Vision at full strength for the player, without the effect. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void nexus$fullbright(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (entity == Minecraft.getInstance().player && NexusCore.get().get(Mods.Fullbright.class).isActive()) {
            cir.setReturnValue(1.0F);
        }
    }
}
