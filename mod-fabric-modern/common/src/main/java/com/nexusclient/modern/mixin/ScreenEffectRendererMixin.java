package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low fire: moves the fire that covers your screen when you burn further down, so you can see.
 * The method's inputs differ between versions, so the drawing position ("PoseStack") is picked out
 * by its type with @Local instead of by its place in the list.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    /** True while we've moved the fire down (so we move it back afterwards). */
    @Unique
    private static boolean nexus$lowered;

    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void nexus$lowFireStart(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        Mods.LowFire lowFire = NexusCore.get().get(Mods.LowFire.class);
        nexus$lowered = lowFire.isActive();
        if (nexus$lowered) {
            poseStack.pushPose();
            poseStack.translate(0, -(1 - lowFire.height.get()) * 0.6, 0);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void nexus$lowFireEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        if (nexus$lowered) {
            poseStack.popPose();
            nexus$lowered = false;
        }
    }
}
