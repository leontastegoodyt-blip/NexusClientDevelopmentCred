package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Block outline colour for 1.21.4: the outline is drawn with ShapeRenderer.renderShape(…, colour),
 * one packed colour number. (The line width is fixed in this version's line shader.)
 */
@Mixin(LevelRenderer.class)
public class BlockOutlineMixin {

    private static final String RENDER_SHAPE = "Lnet/minecraft/client/renderer/ShapeRenderer;renderShape("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/phys/shapes/VoxelShape;DDDI)V";

    @ModifyArg(method = "renderHitOutline", at = @At(value = "INVOKE", target = RENDER_SHAPE), index = 6)
    private int nexus$outlineColour(int colour) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        return outline.isActive() ? outline.color.argb() : colour;
    }
}
