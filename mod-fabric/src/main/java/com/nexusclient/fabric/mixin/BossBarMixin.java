package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.CustomBossBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Minecraft's own boss bars while the Nexus Boss Bar (Custom Boss Bar) is on. */
@Mixin(BossHealthOverlay.class)
public class BossBarMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void nexus$hideBossBars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (NexusCore.get().get(CustomBossBar.class).replacesVanilla()) {
            ci.cancel();
        }
    }
}
