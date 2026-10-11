package com.nexusclient.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item Physics, part 2: dropped items stop spinning (see ItemPhysicsMixin). */
@Mixin(ItemEntity.class)
public class ItemSpinMixin {

    @ModifyReturnValue(method = "getSpin", at = @At("RETURN"))
    private static float nexus$noSpin(float spin) {
        return NexusCore.get().get(Mods.ItemPhysics.class).isActive() ? 0 : spin;
    }
}
