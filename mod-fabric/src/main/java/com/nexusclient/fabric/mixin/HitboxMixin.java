package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hitboxes (F3+B) for 26.x: boxes are drawn as debug "gizmos" in EntityHitboxDebugRenderer. The
 * main box is white (-1); boxes the server sends are green and are left alone.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public class HitboxMixin {

    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void nexus$playersOnly(Entity entity, float partialTick, boolean serverEntity, CallbackInfo ci) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        if (hitboxes.isActive() && hitboxes.showOnPlayersOnly.isOn() && !(entity instanceof Player)) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/gizmos/GizmoStyle;stroke(I)Lnet/minecraft/gizmos/GizmoStyle;"))
    private int nexus$hitboxColour(int colour) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        return hitboxes.isActive() && colour == -1 ? hitboxes.color.argb() : colour;
    }
}
