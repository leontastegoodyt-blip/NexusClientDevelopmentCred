package com.nexusclient.modern.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Hit Colour repaint the small texture Minecraft uses to tint hurt mobs and players. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {

    @Accessor("texture")
    DynamicTexture nexus$texture();
}
