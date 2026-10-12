package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.CustomBossBar;
import com.nexusclient.core.hud.CustomScoreboard;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides Minecraft's own sidebar scoreboard and boss bar while the Nexus Scoreboard / Boss Bar are on
 * (1.8.9).
 */
@Mixin(InGameHud.class)
public class VanillaHudHideMixin {

    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void nexus$hideSidebar(ScoreboardObjective objective, Window window, CallbackInfo ci) {
        if (NexusCore.get().get(CustomScoreboard.class).replacesVanilla()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true)
    private void nexus$hideBossBar(CallbackInfo ci) {
        if (NexusCore.get().get(CustomBossBar.class).replacesVanilla()) {
            // Minecraft counts the bar's time down while drawing it – keep doing that, or an old boss bar
            // would never go away.
            if (net.minecraft.entity.boss.BossBar.framesToLive > 0) {
                net.minecraft.entity.boss.BossBar.framesToLive--;
            }
            ci.cancel();
        }
    }
}
