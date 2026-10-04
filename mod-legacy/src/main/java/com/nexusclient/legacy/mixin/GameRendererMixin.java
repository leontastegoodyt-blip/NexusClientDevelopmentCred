package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.legacy.ZoomControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera and lighting mods:
 *  - Zoom: a smaller field of view (FOV) makes everything look closer
 *  - No Hurt-Cam Shake: skips the screen tilt when you take damage
 *  - Fullbright: lights the world as if brightness were turned far past "Bright" (only on your screen)
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nexus$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        double zoom = ZoomControl.amount();
        if (zoom > 1.001) {
            cir.setReturnValue((float) (cir.getReturnValue() / zoom));
        }
    }

    @Inject(method = "bobViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void nexus$noHurtCam(float tickDelta, CallbackInfo ci) {
        if (NexusCore.get().get(Mods.NoHurtCam.class).isActive()) {
            ci.cancel();
        }
    }

    /** The player's real direction, put back after the camera is placed (freelook). */
    @Unique
    private float nexus$yaw, nexus$pitch, nexus$prevYaw, nexus$prevPitch;
    @Unique
    private boolean nexus$swapped;

    /**
     * Freelook: while the camera is being placed, the player briefly "looks" where the free camera
     * looks, then gets their real direction back straight after – so only the camera turns.
     */
    @Inject(method = "transformCamera", at = @At("HEAD"))
    private void nexus$freelookStart(float tickDelta, CallbackInfo ci) {
        Mods.Freelook freelook = NexusCore.get().get(Mods.Freelook.class);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        nexus$swapped = freelook.looking && player != null;
        if (nexus$swapped) {
            nexus$yaw = player.yaw;
            nexus$pitch = player.pitch;
            nexus$prevYaw = player.prevYaw;
            nexus$prevPitch = player.prevPitch;
            player.yaw = player.prevYaw = freelook.yaw;
            player.pitch = player.prevPitch = freelook.pitch;
        }
    }

    @Inject(method = "transformCamera", at = @At("RETURN"))
    private void nexus$freelookEnd(float tickDelta, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (nexus$swapped && player != null) {
            player.yaw = nexus$yaw;
            player.pitch = nexus$pitch;
            player.prevYaw = nexus$prevYaw;
            player.prevPitch = nexus$prevPitch;
        }
        nexus$swapped = false;
    }

    @Redirect(method = "updateLightmap", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;gamma:F", opcode = Opcodes.GETFIELD))
    private float nexus$fullbright(GameOptions options) {
        return NexusCore.get().get(Mods.Fullbright.class).isActive() ? 16.0F : options.gamma;
    }
}
