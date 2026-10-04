package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleType;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle multiplier (1.8.9): when Minecraft makes the sparks for a hit (critical and
 * enchanted/"magic" crits), make them several times over – or not at all when it's 0.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Shadow
    public abstract void addEmitter(Entity entity, ParticleType type);

    /** True while we're making the extra copies (so they don't make copies of their own). */
    @Unique
    private boolean nexus$repeating;

    @Inject(method = "addEmitter", at = @At("HEAD"), cancellable = true)
    private void nexus$hitParticles(Entity entity, ParticleType type, CallbackInfo ci) {
        Mods.Particles particles = NexusCore.get().get(Mods.Particles.class);
        if (nexus$repeating || !particles.isActive() || (type != ParticleType.CRIT && type != ParticleType.CRIT_MAGIC)) {
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
                addEmitter(entity, type);
            }
        } finally {
            nexus$repeating = false;
        }
    }
}
