package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Enchant Glint Colour on 1.8.9 (items). 1.8.9 draws the shimmer twice with the colour 0xFF8040CC
 * (the purple you know); we hand it the chosen colour instead. Armour: ArmorGlintColourMixin.
 */
@Mixin(ItemRenderer.class)
public class GlintColourMixin {

    @ModifyArg(method = "renderGlint",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;I)V"),
            index = 1)
    private int nexus$glintColour(int colour) {
        Mods.EnchantGlint glint = NexusCore.get().get(Mods.EnchantGlint.class);
        return glint.isActive() ? 0xFF000000 | (glint.color.get() & 0xFFFFFF) : colour;
    }
}
