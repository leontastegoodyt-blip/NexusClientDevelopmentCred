package com.nexusclient.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: moves the fire that covers your screen when you burn further down, so you can see. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    /** True while we've moved the fire down (so we move it back afterwards). */
    @Unique
    private static boolean nexus$lowered;

    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void nexus$lowFireStart(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite,
                                           CallbackInfo ci) {
        Mods.LowFire lowFire = NexusCore.get().get(Mods.LowFire.class);
        nexus$lowered = lowFire.isActive();
        if (nexus$lowered) {
            poseStack.pushPose();
            poseStack.translate(0, -(1 - lowFire.height.get()) * 0.6, 0);
        }
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void nexus$lowFireEnd(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite,
                                         CallbackInfo ci) {
        if (nexus$lowered) {
            poseStack.popPose();
            nexus$lowered = false;
        }
    }
}
