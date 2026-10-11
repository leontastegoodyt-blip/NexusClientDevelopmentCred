package com.nexusclient.legacy.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.7 Animations (1.8.9 only): the famous "block-hit" look from Minecraft 1.7.
 *
 * While you block with a sword, eat, drink or draw a bow, 1.8 holds the item still – it passes 0 as
 * the swing amount when placing the item in your hand. 1.7 passed the real swing, so the sword
 * visibly swings while blocking. We give the real swing back. (The swing itself is started by
 * NexusLegacy when you click while blocking – only on your screen, nothing is sent to the server.)
 */
@Mixin(HeldItemRenderer.class)
public class OldAnimationsMixin {

    @ModifyArg(method = "renderArmHoldingItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V"),
            index = 1)
    private float nexus$swingWhileUsing(float swing, @Local(argsOnly = true) float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (swing == 0 && mc.player != null && NexusCore.get().get(Mods.OldAnimations.class).isActive()) {
            return mc.player.getHandSwingProgress(tickDelta);
        }
        return swing;
    }
}
