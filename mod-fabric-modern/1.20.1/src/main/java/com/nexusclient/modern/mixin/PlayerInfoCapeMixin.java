package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nexusclient.modern.VersionHooks;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nexus capes on 1.20.1: players wearing one get it instead of their normal cape and elytra (only on
 * Nexus players' screens – see mod-core Capes).
 */
@Mixin(PlayerInfo.class)
public class PlayerInfoCapeMixin {

    @ModifyReturnValue(method = {"getCapeLocation", "getElytraLocation"}, at = @At("RETURN"))
    private ResourceLocation nexus$cape(ResourceLocation original) {
        ResourceLocation cape = VersionHooks.capeTexture(((PlayerInfo) (Object) this).getProfile().getId());
        return cape != null ? cape : original;
    }
}
