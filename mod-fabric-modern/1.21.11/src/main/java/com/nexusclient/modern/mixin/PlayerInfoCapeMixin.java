package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nexusclient.modern.VersionHooks;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nexus capes on 1.21.11: players wearing one get it instead of their normal cape and elytra (only on
 * Nexus players' screens – see mod-core Capes).
 */
@Mixin(PlayerInfo.class)
public class PlayerInfoCapeMixin {

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin nexus$cape(PlayerSkin skin) {
        ClientAsset.Texture cape = VersionHooks.capeTexture(((PlayerInfo) (Object) this).getProfile().id());
        if (cape == null || skin == null) {
            return skin;
        }
        return new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
    }
}
