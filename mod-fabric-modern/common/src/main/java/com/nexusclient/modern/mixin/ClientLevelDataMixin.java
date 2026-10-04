package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time changer: the sky, sun, moon and light read the time from the client's own copy of the world
 * info (ClientLevelData). Changing it here only changes what this player sees – never the real
 * world or the server's time.
 */
@Mixin(ClientLevel.ClientLevelData.class)
public class ClientLevelDataMixin {

    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void nexus$timeChanger(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(NexusCore.get().get(Mods.TimeChanger.class).apply(cir.getReturnValue()));
    }
}
