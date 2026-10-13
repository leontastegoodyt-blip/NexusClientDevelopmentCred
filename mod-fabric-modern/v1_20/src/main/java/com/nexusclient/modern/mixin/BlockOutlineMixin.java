package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Block outline colour for 1.20 – 1.20.6 (this "v1_20" folder is shared by those builds).
 * Minecraft draws the outline with renderShape(…, red, green, blue, alpha) – we swap those four,
 * one @ModifyArg each (@ModifyArgs would be shorter, but it doesn't work on Forge).
 * (These versions fix the line width inside their line shader, so thickness can't change here.)
 */
@Mixin(LevelRenderer.class)
public class BlockOutlineMixin {

    private static final String RENDER_SHAPE = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V";

    @ModifyArg(method = "renderHitOutline", at = @At(value = "INVOKE", target = RENDER_SHAPE), index = 6)
    private float nexus$red(float red) {
        return channel(red, 16);
    }

    @ModifyArg(method = "renderHitOutline", at = @At(value = "INVOKE", target = RENDER_SHAPE), index = 7)
    private float nexus$green(float green) {
        return channel(green, 8);
    }

    @ModifyArg(method = "renderHitOutline", at = @At(value = "INVOKE", target = RENDER_SHAPE), index = 8)
    private float nexus$blue(float blue) {
        return channel(blue, 0);
    }

    @ModifyArg(method = "renderHitOutline", at = @At(value = "INVOKE", target = RENDER_SHAPE), index = 9)
    private float nexus$alpha(float alpha) {
        return channel(alpha, 24);
    }

    /** One colour channel (0–1) of the chosen outline colour, or Minecraft's own while the mod is off. */
    private static float channel(float original, int shift) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        return outline.isActive() ? ((outline.color.argb() >>> shift) & 0xFF) / 255f : original;
    }
}
