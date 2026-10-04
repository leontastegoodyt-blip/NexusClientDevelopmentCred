package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small items: draws the item in your hand smaller (first person only), so you can see more.
 * The inputs differ between versions, so they're picked out by type with @Local.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    /** True while we've shrunk the item (so we undo it afterwards). */
    @Unique
    private boolean nexus$shrunk;

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void nexus$smallItemsStart(CallbackInfo ci, @Local(argsOnly = true) ItemDisplayContext context,
                                       @Local(argsOnly = true) PoseStack poseStack) {
        Mods.SmallItems smallItems = NexusCore.get().get(Mods.SmallItems.class);
        nexus$shrunk = smallItems.isActive() && context.firstPerson();
        if (nexus$shrunk) {
            float scale = smallItems.scale.get().floatValue();
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void nexus$smallItemsEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        if (nexus$shrunk) {
            poseStack.popPose();
            nexus$shrunk = false;
        }
    }
}
