package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Block outline for 26.x: submitHitOutline gets the colour and the line width as inputs, so we
 * swap both on the way in.
 */
@Mixin(LevelRenderer.class)
public class BlockOutlineMixin {

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true)
    private int nexus$outlineColour(int colour) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        return outline.isActive() ? outline.color.argb() : colour;
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true)
    private float nexus$outlineWidth(float width) {
        Mods.BlockOutline outline = NexusCore.get().get(Mods.BlockOutline.class);
        return outline.isActive() ? outline.thickness.get().floatValue() : width;
    }
}
