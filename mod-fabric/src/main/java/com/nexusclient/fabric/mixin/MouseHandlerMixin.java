package com.nexusclient.fabric.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.InputTracker;
import com.nexusclient.core.module.Mods;
import com.nexusclient.fabric.NexusScreenWrapper;
import com.nexusclient.fabric.ZoomControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the mouse:
 *  - counts clicks for the CPS HUD (only real clicks in-game, not in menus)
 *  - scrolling while zoomed changes the zoom instead of the hotbar slot
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void nexus$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1 && com.nexusclient.fabric.Compat.screen(Minecraft.getInstance()) == null) { // 1 = pressed
            InputTracker.click(NexusScreenWrapper.toCoreButton(info.button()));
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nexus$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset != 0 && ZoomControl.zooming() && NexusCore.get().get(Mods.Zoom.class).scroll.isOn()) {
            ZoomControl.scroll(yOffset);
            ci.cancel();
        }
    }
}
