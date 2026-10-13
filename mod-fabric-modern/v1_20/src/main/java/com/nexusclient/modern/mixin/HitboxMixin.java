package com.nexusclient.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes (F3+B) for 1.20 – 1.20.6: the box colour, and optionally only players' boxes. */
@Mixin(EntityRenderDispatcher.class)
public class HitboxMixin {

    private static final String LINE_BOX = "Lnet/minecraft/client/renderer/LevelRenderer;renderLineBox("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/phys/AABB;FFFF)V";

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void nexus$playersOnly(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTick,
                                          CallbackInfo ci) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        if (hitboxes.isActive() && hitboxes.showOnPlayersOnly.isOn() && !(entity instanceof Player)) {
            ci.cancel();
        }
    }

    // The first box drawn is the entity's main (white) hitbox: its red, green, blue and alpha (arguments
    // 3–6) are swapped, one @ModifyArg each (@ModifyArgs would be shorter, but it doesn't work on Forge).

    @ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = LINE_BOX, ordinal = 0), index = 3)
    private static float nexus$red(float red) {
        return channel(red, 16);
    }

    @ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = LINE_BOX, ordinal = 0), index = 4)
    private static float nexus$green(float green) {
        return channel(green, 8);
    }

    @ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = LINE_BOX, ordinal = 0), index = 5)
    private static float nexus$blue(float blue) {
        return channel(blue, 0);
    }

    @ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = LINE_BOX, ordinal = 0), index = 6)
    private static float nexus$alpha(float alpha) {
        return channel(alpha, 24);
    }

    /** One colour channel (0–1) of the chosen hitbox colour, or Minecraft's own while the mod is off. */
    private static float channel(float original, int shift) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        return hitboxes.isActive() ? ((hitboxes.color.argb() >>> shift) & 0xFF) / 255f : original;
    }
}
