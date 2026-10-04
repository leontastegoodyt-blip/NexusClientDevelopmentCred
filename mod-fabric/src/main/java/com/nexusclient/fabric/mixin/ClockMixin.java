package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time changer for 26.x: time now comes from "world clocks", and the client keeps its own copy of
 * them (ClientClockManager). Changing what that copy says only changes what this player sees –
 * never the real world or the server's time.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public class ClockMixin {

    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void nexus$timeChanger(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(NexusCore.get().get(Mods.TimeChanger.class).apply(cir.getReturnValue()));
    }

    /** While the time is fixed, stop the in-between smoothing so the sky doesn't creep forward. */
    @Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
    private void nexus$freeze(CallbackInfoReturnable<Float> cir) {
        if (NexusCore.get().get(Mods.TimeChanger.class).isActive()) {
            cir.setReturnValue(0.0F);
        }
    }
}
