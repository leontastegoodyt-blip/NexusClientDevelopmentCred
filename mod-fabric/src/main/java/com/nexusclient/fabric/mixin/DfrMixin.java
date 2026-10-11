package com.nexusclient.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
            target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void nexus$beforeShow(GpuSurface surface, CommandEncoder encoder, GpuTextureView frame, Operation<Void> original) {
        DfrRenderer.beforeShow(surface, frame);
        // Motion Blur may hand back a blurred copy of the frame to show instead.
        GpuTextureView shown = DfrRenderer.motionBlur(frame);
        nexus$shownFrame = shown;
        original.call(surface, encoder, shown);
    }

    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuSurface;present()V"))
    private void nexus$afterShow(GpuSurface surface, Operation<Void> original) {
        original.call(surface);
        DfrRenderer.afterShow(surface, nexus$shownFrame);
    }
}
