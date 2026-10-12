package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.CustomScoreboard;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Minecraft's own sidebar scoreboard while the Nexus Scoreboard (Custom Scoreboard) is on. */
@Mixin(Gui.class)
public class VanillaHudHideMixin {

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void nexus$hideSidebar(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        if (NexusCore.get().get(CustomScoreboard.class).replacesVanilla()) {
            ci.cancel();
        }
    }
}
