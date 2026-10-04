package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook, part 2: while freelook is on, moving the mouse turns the free camera instead of the
 * player (Minecraft turns the player with Entity.turn).
 */
@Mixin(Entity.class)
public class TurnMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void nexus$freelookTurn(double dx, double dy, CallbackInfo ci) {
        Mods.Freelook freelook = NexusCore.get().get(Mods.Freelook.class);
        if (freelook.looking && (Object) this == Minecraft.getInstance().player) {
            freelook.turn(dx, dy);
            ci.cancel();
        }
    }
}
