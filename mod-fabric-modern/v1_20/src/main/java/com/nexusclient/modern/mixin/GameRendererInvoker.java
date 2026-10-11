package com.nexusclient.modern.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets Nexus call GameRenderer.loadEffect, which Minecraft keeps to itself (it's "private").
 * Motion Blur on 1.20.x uses it to switch on a full-screen effect (see MotionBlurControl).
 */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("loadEffect")
    void nexus$loadEffect(ResourceLocation effect);
}
