package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: while it's on, moving the mouse turns the free camera instead of the player
 * (1.8.9 turns the player with Entity.increaseTransforms – its "pitch" goes up when the mouse
 * goes up, so it's flipped to match the newer versions).
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "increaseTransforms", at = @At("HEAD"), cancellable = true)
    private void nexus$freelookTurn(float yaw, float pitch, CallbackInfo ci) {
        Mods.Freelook freelook = NexusCore.get().get(Mods.Freelook.class);
        if (freelook.looking && (Object) this == MinecraftClient.getInstance().player) {
            freelook.turn(yaw, -pitch);
            ci.cancel();
        }
    }
}
