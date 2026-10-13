package com.nexusclient.legacy.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Nexus add its own feature (the Halo and Neon Wings) to the player renderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {

    @Invoker("addFeature")
    boolean nexus$addFeature(FeatureRenderer<?> feature);
}
