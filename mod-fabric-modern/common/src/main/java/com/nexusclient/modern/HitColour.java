package com.nexusclient.modern;

import com.mojang.blaze3d.platform.NativeImage;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import com.nexusclient.modern.mixin.OverlayTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hit colour: mobs and players flash red when hurt because Minecraft tints them with a tiny 16x16
 * "overlay" texture whose top 8 rows are see-through red. Whenever the chosen colour changes (or
 * the mod is switched on/off), those rows are repainted and the texture is sent to the graphics card
 * again. Called every tick; it only does work when something changed.
 */
public final class HitColour {

    /** The colour currently painted (0 = not painted by us yet). */
    private static int painted = 0;

    private HitColour() {}

    public static void tick(Minecraft mc) {
        Mods.HitColor hitColor = NexusCore.get().get(Mods.HitColor.class);
        int wanted = hitColor.isActive() ? hitColor.color.argb() : VersionHooks.DEFAULT_HURT_COLOUR;
        if (wanted == painted || mc.gameRenderer == null) {
            return;
        }
        DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).nexus$texture();
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int y = 0; y < 8; y++) {      // the "hurt" rows
            for (int x = 0; x < 16; x++) {
                VersionHooks.paintPixel(pixels, x, y, wanted);
            }
        }
        texture.upload();
        painted = wanted;
    }
}
