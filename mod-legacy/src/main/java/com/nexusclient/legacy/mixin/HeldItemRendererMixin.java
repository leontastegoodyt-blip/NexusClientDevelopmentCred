package com.nexusclient.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person drawing in 1.8.9:
 *  - Low fire: moves the fire that covers your screen when you burn further down
 *  - Small items: draws the item in your hand smaller
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    private static final String RENDER_ITEM = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem("
            + "Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/client/render/model/json/ModelTransformation$Mode;)V";

    @Unique
    private boolean nexus$lowered;
    @Unique
    private boolean nexus$shrunk;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private void nexus$lowFireStart(float tickDelta, CallbackInfo ci) {
        Mods.LowFire lowFire = NexusCore.get().get(Mods.LowFire.class);
        nexus$lowered = lowFire.isActive();
        if (nexus$lowered) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, -(1 - lowFire.height.get()) * 0.6, 0);
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private void nexus$lowFireEnd(float tickDelta, CallbackInfo ci) {
        if (nexus$lowered) {
            GlStateManager.popMatrix();
            nexus$lowered = false;
        }
    }

    @Inject(method = "renderArmHoldingItem", at = @At(value = "INVOKE", target = RENDER_ITEM))
    private void nexus$smallItemsStart(float tickDelta, CallbackInfo ci) {
        Mods.SmallItems smallItems = NexusCore.get().get(Mods.SmallItems.class);
        nexus$shrunk = smallItems.isActive();
        if (nexus$shrunk) {
            float scale = smallItems.scale.get().floatValue();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, scale);
        }
    }

    @Inject(method = "renderArmHoldingItem", at = @At(value = "INVOKE", target = RENDER_ITEM, shift = At.Shift.AFTER))
    private void nexus$smallItemsEnd(float tickDelta, CallbackInfo ci) {
        if (nexus$shrunk) {
            GlStateManager.popMatrix();
            nexus$shrunk = false;
        }
    }
}
