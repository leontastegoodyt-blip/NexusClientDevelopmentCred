package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook, part 1: while the camera is being placed, the player briefly "looks" where the free
 * camera looks, then gets their real direction back straight after. So only the camera turns –
 * the player model, walking direction and aim stay the same. (Part 2, TurnMixin, steers the camera.)
 */
@Mixin(Camera.class)
public class FreelookMixin {
    // (26.x places the camera in Camera.alignWithEntity – older versions in Camera.setup.)

    /** The player's real direction, put back after the camera is placed. */
    @Unique
    private float nexus$yRot;
    @Unique
    private float nexus$xRot;
    @Unique
    private float nexus$yRotO;
    @Unique
    private float nexus$xRotO;
    @Unique
    private boolean nexus$swapped;

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void nexus$freelookStart(CallbackInfo ci) {
        Mods.Freelook freelook = NexusCore.get().get(Mods.Freelook.class);
        LocalPlayer player = Minecraft.getInstance().player;
        nexus$swapped = freelook.looking && player != null;
        if (nexus$swapped) {
            nexus$yRot = player.getYRot();
            nexus$xRot = player.getXRot();
            nexus$yRotO = player.yRotO;
            nexus$xRotO = player.xRotO;
            player.setYRot(freelook.yaw);
            player.setXRot(freelook.pitch);
            player.yRotO = freelook.yaw;
            player.xRotO = freelook.pitch;
        }
    }

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void nexus$freelookEnd(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (nexus$swapped && player != null) {
            player.setYRot(nexus$yRot);
            player.setXRot(nexus$xRot);
            player.yRotO = nexus$yRotO;
            player.xRotO = nexus$xRotO;
        }
        nexus$swapped = false;
    }
}
