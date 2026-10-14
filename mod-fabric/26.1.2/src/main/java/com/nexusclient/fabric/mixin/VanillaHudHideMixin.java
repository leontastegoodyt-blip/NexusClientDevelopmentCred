package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.CustomScoreboard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Minecraft's own sidebar scoreboard while the Nexus Scoreboard is on (26.1: the HUD is still Gui). */
@Mixin(Gui.class)
public class VanillaHudHideMixin {

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void nexus$hideSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (NexusCore.get().get(CustomScoreboard.class).replacesVanilla()) {
            ci.cancel();
        }
    }
}
