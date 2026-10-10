package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.nexusclient.modern.BadgeText;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nexus badges in name tags, 1.21.2+: Minecraft asks getNameTag(entity) for the text of each name tag
 * (it's stored in the entity's "render state" before drawing). We add the badge to what it returns.
 */
@Mixin(EntityRenderer.class)
public class BadgeMixin {

    @ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
    private Component nexus$badge(Component name, @Local(argsOnly = true) Entity entity) {
        return BadgeText.decorate(entity, name);
    }
}
