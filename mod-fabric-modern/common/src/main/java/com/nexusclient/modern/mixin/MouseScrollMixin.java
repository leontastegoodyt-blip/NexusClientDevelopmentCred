package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.modern.ZoomControl;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while zoomed changes the zoom instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public class MouseScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nexus$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset != 0 && ZoomControl.zooming() && NexusCore.get().get(Mods.Zoom.class).scroll.isOn()) {
            ZoomControl.scroll(yOffset);
            ci.cancel();
        }
    }
}
