package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Living things in 1.8.9:
 *  - Custom nametags: shows your own name above your head in third person (F5)
 *  - Hit colour: the tint mobs and players flash when hurt. method_10252 writes the hurt tint as
 *    four numbers (red 1, green 0, blue 0, strength 0.3) – the first four "put" calls – and we swap them.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    private static final String PUT = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;";

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 0))
    private float nexus$hurtRed(float red) {
        return hitColour(red, 16);
    }

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 1))
    private float nexus$hurtGreen(float green) {
        return hitColour(green, 8);
    }

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 2))
    private float nexus$hurtBlue(float blue) {
        return hitColour(blue, 0);
    }

    @ModifyArg(method = "method_10252", at = @At(value = "INVOKE", target = PUT, ordinal = 3))
    private float nexus$hurtStrength(float strength) {
        return hitColour(strength, 24);
    }

    /** One part of the chosen hit colour (shift 16 = red, 8 = green, 0 = blue, 24 = see-through). */
    private static float hitColour(float original, int shift) {
        Mods.HitColor hitColor = NexusCore.get().get(Mods.HitColor.class);
        return hitColor.isActive() ? ((hitColor.color.argb() >>> shift) & 0xFF) / 255f : original;
    }

    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void nexus$ownNametag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        Mods.Nametags nametags = NexusCore.get().get(Mods.Nametags.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (nametags.isActive() && nametags.ownNametag.isOn() && entity == mc.player && !mc.options.hudHidden) {
            cir.setReturnValue(true);
        }
    }
}
