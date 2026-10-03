package com.nexusclient.modern.mixin;

import com.nexusclient.core.hud.InputTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts clicks for the CPS HUD (only real clicks in-game, not in menus). For 1.21.11. */
@Mixin(MouseHandler.class)
public class MouseClickMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void nexus$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1 && Minecraft.getInstance().screen == null) { // 1 = pressed
            InputTracker.click(info.button()); // 0 = left, 1 = right, 2 = middle (same as mod-core)
        }
    }
}
