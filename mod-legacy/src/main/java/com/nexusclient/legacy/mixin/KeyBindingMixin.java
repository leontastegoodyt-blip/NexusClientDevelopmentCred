package com.nexusclient.legacy.mixin;

import com.nexusclient.core.hud.InputTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts clicks for the CPS HUD. In 1.8.9 every key and mouse press in-game goes through
 * KeyBinding.onKeyPressed; mouse buttons are numbered from -100 (left = -100, right = -99, middle = -98).
 */
@Mixin(KeyBinding.class)
public class KeyBindingMixin {

    @Inject(method = "onKeyPressed", at = @At("HEAD"))
    private static void nexus$countClick(int keyCode, CallbackInfo ci) {
        if (keyCode >= -100 && keyCode <= -98 && MinecraftClient.getInstance().currentScreen == null) {
            InputTracker.click(keyCode + 100); // 0 = left, 1 = right, 2 = middle
        }
    }
}
