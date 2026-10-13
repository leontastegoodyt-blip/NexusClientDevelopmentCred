package com.nexusclient.fabric.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Nexus add its own layer (the Halo and Neon Wings) to the player renderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {

    @Invoker("addLayer")
    boolean nexus$addLayer(RenderLayer<?, ?> layer);
}
