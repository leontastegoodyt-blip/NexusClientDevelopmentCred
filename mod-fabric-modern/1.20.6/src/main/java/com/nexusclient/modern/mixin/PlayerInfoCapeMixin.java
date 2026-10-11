package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nexusclient.modern.VersionHooks;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nexus capes: players wearing one get it instead of their normal cape and elytra (only on Nexus
 * players' screens – see mod-core Capes). From 1.20.2, a player's skin, cape and elytra come together
 * as one "PlayerSkin", so we hand back a copy with the Nexus cape in it.
 */
@Mixin(PlayerInfo.class)
public class PlayerInfoCapeMixin {

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin nexus$cape(PlayerSkin skin) {
        ResourceLocation cape = VersionHooks.capeTexture(((PlayerInfo) (Object) this).getProfile().getId());
        if (cape == null || skin == null) {
            return skin;
        }
        return new PlayerSkin(skin.texture(), skin.textureUrl(), cape, cape, skin.model(), skin.secure());
    }
}
