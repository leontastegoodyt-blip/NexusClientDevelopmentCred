package com.nexusclient.fabric.mixin;

import com.nexusclient.fabric.Cosmetics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nexus capes: players wearing one get it instead of their normal cape (only on Nexus players' screens). */
@Mixin(PlayerInfo.class)
public class PlayerInfoMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void nexus$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        ClientAsset.Texture cape = Cosmetics.capeFor(self.getProfile().id());
        if (cape != null) {
            PlayerSkin skin = cir.getReturnValue();
            cir.setReturnValue(new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure()));
        }
    }
}
