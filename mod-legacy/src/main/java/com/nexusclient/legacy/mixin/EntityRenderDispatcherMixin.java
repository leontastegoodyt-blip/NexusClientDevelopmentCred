package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Hitboxes (1.8.9, F3+B): the box colour, and optionally only players' boxes. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private void nexus$playersOnly(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                                   CallbackInfo ci) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        if (hitboxes.isActive() && hitboxes.showOnPlayersOnly.isOn() && !(entity instanceof PlayerEntity)) {
            ci.cancel();
        }
    }

    @ModifyArgs(method = "renderHitbox", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/util/math/Box;IIII)V"))
    private void nexus$hitboxColour(Args args) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        if (hitboxes.isActive()) {
            int argb = hitboxes.color.argb();
            args.set(1, (argb >> 16) & 0xFF);
            args.set(2, (argb >> 8) & 0xFF);
            args.set(3, argb & 0xFF);
            args.set(4, (argb >>> 24) & 0xFF);
        }
    }
}
