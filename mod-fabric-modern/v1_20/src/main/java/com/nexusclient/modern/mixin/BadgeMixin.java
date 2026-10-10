package com.nexusclient.modern.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nexusclient.modern.BadgeText;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Nexus badges in name tags, 1.20.x: when a name tag is about to be drawn, swap its text for the
 * same name with the badge in front (BadgeText). The player's score line (drawn by the same method)
 * is left alone.
 */
@Mixin(EntityRenderer.class)
public class BadgeMixin {

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component nexus$badge(Component text, @Local(argsOnly = true) Entity entity) {
        return BadgeText.isOwnName(entity, text) ? BadgeText.decorate(entity, text) : text;
    }
}
