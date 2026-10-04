package com.nexusclient.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.render.WorldRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Block outline (1.8.9): drawBlockOutline sets the colour (black, 40% see-through) and the line
 * width (2) just before drawing – we swap in the chosen colour and thickness.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V", ordinal = 0))
    private void nexus$outlineColour(float red, float green, float blue, float alpha) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        if (outline.isActive()) {
            int argb = outline.color.argb();
            GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                    ((argb >>> 24) & 0xFF) / 255f);
        } else {
            GlStateManager.color(red, green, blue, alpha);
        }
    }

    @Redirect(method = "drawBlockOutline", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V"))
    private void nexus$outlineWidth(float width) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        GL11.glLineWidth(outline.isActive() ? outline.thickness.get().floatValue() : width);
    }
}
