package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.modern.VersionHooks;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright: lights the world as if you had Night Vision (only on your screen).
 * Minecraft's light map asks "does the player have Night Vision?" – with Fullbright on, the answer
 * is always yes (and GameRendererMixin makes it full strength).
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

    // The exact method differs between versions, so it comes from VersionHooks. (It must be exact:
    // the build translates it into the real game's scrambled names, which needs the full signature.)
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = VersionHooks.HAS_EFFECT, ordinal = 0))
    private boolean nexus$fullbright(boolean hasNightVision) {
        return hasNightVision || NexusCore.get().get(Mods.Fullbright.class).isActive();
    }
}
