package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics: dropped items lie flat on the ground instead of floating, bobbing and spinning.
 *
 * Minecraft draws a dropped item in three steps: lift it by the "bob" height (translate), turn it by
 * the "spin" angle, then draw it. We (1) replace the bob height with "just above the ground", (2) lay
 * the item flat by turning it 90° right after that lift, and (3) ItemSpinMixin makes the spin 0.
 * "render" is the method's name up to 1.21.4, "submit" from 1.21.9 on (only one exists per version).
 */
@Mixin(ItemEntityRenderer.class)
public class ItemPhysicsMixin {

    @ModifyArg(method = {"render", "submit"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0),
            index = 1, require = 0)
    private float nexus$noBob(float y) {
        return active() ? 0.05f : y;
    }

    @Inject(method = {"render", "submit"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0,
                    shift = At.Shift.AFTER),
            require = 0)
    private void nexus$layFlat(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        if (active()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }
    }

    private static boolean active() {
        return NexusCore.get().get(Mods.ItemPhysics.class).isActive();
    }
}
