package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.NexusLegacy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the Nexus HUD on top of Minecraft's, and hides Minecraft's crosshair when Nexus draws its own. */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void nexus$hud(float tickDelta, CallbackInfo ci) {
        NexusLegacy.renderHud();
    }

    @Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true)
    private void nexus$hideCrosshair(CallbackInfoReturnable<Boolean> cir) {
        if (NexusLegacy.customCrosshair(MinecraftClient.getInstance())) {
            cir.setReturnValue(false);
        }
    }
}
