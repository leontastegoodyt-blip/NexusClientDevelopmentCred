package com.nexusclient.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small items (26.x): shrinks the item in your hand. 26.x draws held items in
 * FirstPersonHandsAndItemsRenderer.submitArmWithItem; we shrink just around the moment the item
 * itself is drawn ("ItemStackRenderState.submit"), so the arm stays normal size.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonItemMixin {

    private static final String SUBMIT_ITEM = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    /** True while we've shrunk the item (so we undo it afterwards). */
    @Unique
    private boolean nexus$shrunk;

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT_ITEM))
    private void nexus$smallItemsStart(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        Mods.SmallItems smallItems = NexusCore.get().get(Mods.SmallItems.class);
        nexus$shrunk = smallItems.isActive();
        if (nexus$shrunk) {
            float scale = smallItems.scale.get().floatValue();
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT_ITEM, shift = At.Shift.AFTER))
    private void nexus$smallItemsEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        if (nexus$shrunk) {
            poseStack.popPose();
            nexus$shrunk = false;
        }
    }
}
