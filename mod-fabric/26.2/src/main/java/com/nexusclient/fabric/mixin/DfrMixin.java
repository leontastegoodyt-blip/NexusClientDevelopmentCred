package com.nexusclient.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.nexusclient.fabric.DfrRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DFR hooks: wraps the two moments at the end of each frame where Minecraft copies the finished
 * frame to the window and then shows ("presents") it. See DfrRenderer for what happens there.
 */
@Mixin(Minecraft.class)
public class DfrMixin {

    /** The frame that was just copied to the window (for Low lag, after it's shown). */
    @Unique
    private GpuTextureView nexus$shownFrame;

    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"))
    private void nexus$beforeShow(GpuSurface surface, CommandEncoder encoder, GpuTextureView frame, Operation<Void> original) {
        DfrRenderer.beforeShow(surface, frame);
        nexus$shownFrame = frame;
        original.call(surface, encoder, frame);
    }

    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    private void nexus$afterShow(GpuSurface surface, Operation<Void> original) {
        original.call(surface);
        DfrRenderer.afterShow(surface, nexus$shownFrame);
    }
}
