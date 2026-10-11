package com.nexusclient.legacy.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Item Physics on 1.8.9: dropped items lie flat on the ground instead of floating and spinning.
 *
 * 1.8.9 places a dropped item with one move (to its position plus a "bob" height) and one turn
 * (the spin). We move it to its position plus a tiny lift instead, and turn it 90° to lie flat.
 * (method_10221 is 1.8.9's "place the dropped item" step; its name has no readable version.)
 */
@Mixin(ItemEntityRenderer.class)
public class ItemPhysicsMixin {

    @ModifyArg(method = "method_10221",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V", ordinal = 0),
            index = 1)
    private float nexus$noBob(float y, @Local(argsOnly = true, ordinal = 1) double entityY) {
        return active() ? (float) entityY + 0.05f : y;
    }

    @ModifyArgs(method = "method_10221",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;rotate(FFFF)V", ordinal = 0))
    private void nexus$layFlat(Args args) {
        if (active()) {
            args.set(0, 90f);  // a quarter turn…
            args.set(1, 1f);   // …around the left–right line, so it lies down
            args.set(2, 0f);
            args.set(3, 0f);
        }
    }

    private static boolean active() {
        return NexusCore.get().get(Mods.ItemPhysics.class).isActive();
    }
}
