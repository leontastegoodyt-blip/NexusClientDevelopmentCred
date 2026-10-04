package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Block outline colour for 1.20 – 1.20.6 (this "v1_20" folder is shared by those builds).
 * Minecraft draws the outline with renderShape(…, red, green, blue, alpha) – we swap those four.
 * (These versions fix the line width inside their line shader, so thickness can't change here.)
 */
@Mixin(LevelRenderer.class)
public class BlockOutlineMixin {

    private static final String RENDER_SHAPE = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V";

    @ModifyArgs(method = "renderHitOutline", at = @At(value = "INVOKE", target = RENDER_SHAPE))
    private void nexus$outlineColour(Args args) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        if (!outline.isActive()) {
            return;
        }
        int argb = outline.color.argb();
        args.set(6, ((argb >> 16) & 0xFF) / 255f); // red
        args.set(7, ((argb >> 8) & 0xFF) / 255f);  // green
        args.set(8, (argb & 0xFF) / 255f);         // blue
        args.set(9, ((argb >>> 24) & 0xFF) / 255f); // alpha
    }
}
