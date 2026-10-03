package com.nexusclient.modern.mixin;

import com.nexusclient.core.hud.InputTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts clicks for the CPS HUD (only real clicks in-game, not in menus). For 1.20 – 1.21.8. */
@Mixin(MouseHandler.class)
public class MouseClickMixin {

    @Inject(method = "onPress", at = @At("HEAD"))
    private void nexus$countClick(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action == 1 && Minecraft.getInstance().screen == null) { // 1 = pressed
            InputTracker.click(button); // 0 = left, 1 = right, 2 = middle (same as mod-core)
        }
    }
}
