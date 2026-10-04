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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Hitboxes (F3+B) for 1.21.4: the box colour, and optionally only players' boxes. Only white boxes
 * (the normal hitbox) are recoloured – the green ones for dragon parts stay green.
 */
@Mixin(EntityRenderDispatcher.class)
public class HitboxMixin {

    private static final String LINE_BOX = "Lnet/minecraft/client/renderer/ShapeRenderer;renderLineBox("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/phys/AABB;FFFF)V";

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void nexus$playersOnly(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTick,
                                          float red, float green, float blue, CallbackInfo ci) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        if (hitboxes.isActive() && hitboxes.showOnPlayersOnly.isOn() && !(entity instanceof Player)) {
            ci.cancel();
        }
    }

    @ModifyArgs(method = "renderHitbox", at = @At(value = "INVOKE", target = LINE_BOX, ordinal = 0))
    private static void nexus$hitboxColour(Args args) {
        Mods.Hitboxes hitboxes = NexusCore.get().get(Mods.Hitboxes.class);
        boolean white = args.<Float>get(3) == 1f && args.<Float>get(4) == 1f && args.<Float>get(5) == 1f;
        if (hitboxes.isActive() && white) {
            int argb = hitboxes.color.argb();
            args.set(3, ((argb >> 16) & 0xFF) / 255f);
            args.set(4, ((argb >> 8) & 0xFF) / 255f);
            args.set(5, (argb & 0xFF) / 255f);
            args.set(6, ((argb >>> 24) & 0xFF) / 255f);
        }
    }
}
