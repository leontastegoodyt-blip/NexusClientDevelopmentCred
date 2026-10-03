package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.legacy.NexusLegacy;
import com.nexusclient.legacy.ZoomControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Minecraft's main loop:
 *  - runs Nexus's tick at the end of every game tick (20 times a second)
 *  - scrolling while zoomed changes the zoom instead of the hotbar slot
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void nexus$tick(CallbackInfo ci) {
        NexusLegacy.tick((MinecraftClient) (Object) this);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(I)V"))
    private void nexus$zoomScroll(PlayerInventory inventory, int amount) {
        if (ZoomControl.zooming() && NexusCore.get().get(Mods.Zoom.class).scroll.isOn()) {
            ZoomControl.scroll(amount);
        } else {
            inventory.scrollInHotbar(amount);
        }
    }
}
