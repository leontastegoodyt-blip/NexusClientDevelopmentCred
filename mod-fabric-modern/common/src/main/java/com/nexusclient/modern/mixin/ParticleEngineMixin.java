package com.nexusclient.modern.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle multiplier: when Minecraft makes the sparks for a hit (critical hits and enchanted hits),
 * make them several times over – or not at all when the multiplier is 0.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    public abstract void createTrackingEmitter(Entity entity, ParticleOptions particleOptions);

    /** True while we're making the extra copies (so they don't make copies of their own). */
    @Unique
    private boolean nexus$repeating;

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            at = @At("HEAD"), cancellable = true)
    private void nexus$hitParticles(Entity entity, ParticleOptions particleOptions, CallbackInfo ci) {
        Mods.Particles particles = NexusCore.get().get(Mods.Particles.class);
        if (nexus$repeating || !particles.isActive()
                || (particleOptions != ParticleTypes.CRIT && particleOptions != ParticleTypes.ENCHANTED_HIT)) {
            return;
        }
        int times = particles.multiplier.intValue();
        if (times <= 0) {
            ci.cancel(); // no hit particles at all
            return;
        }
        nexus$repeating = true;
        try {
            for (int i = 1; i < times; i++) {
                createTrackingEmitter(entity, particleOptions);
            }
        } finally {
            nexus$repeating = false;
        }
    }
}
