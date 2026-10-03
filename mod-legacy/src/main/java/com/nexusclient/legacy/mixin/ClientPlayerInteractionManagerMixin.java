package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.NexusLegacy;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Combo counter and PvP stats: notices when we hit a player or mob. */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void nexus$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        NexusLegacy.attacked(player, target);
    }
}
