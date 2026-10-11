package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Enchant Glint Colour on 1.8.9 (armour). The armour shimmer is tinted by its second colour call,
 * (0.5, 0.25, 0.8) × 0.76 – purple. We give it the chosen colour × 0.76 instead.
 */
@Mixin(ArmorFeatureRenderer.class)
public class ArmorGlintColourMixin {

    @ModifyArgs(method = "renderGlint",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V", ordinal = 1))
    private void nexus$armourGlint(Args args) {
        Mods.EnchantGlint glint = NexusCore.get().get(Mods.EnchantGlint.class);
        if (glint.isActive()) {
            int rgb = glint.color.get();
            args.set(0, ((rgb >> 16) & 255) / 255f * 0.76f);
            args.set(1, ((rgb >> 8) & 255) / 255f * 0.76f);
            args.set(2, (rgb & 255) / 255f * 0.76f);
        }
    }
}
