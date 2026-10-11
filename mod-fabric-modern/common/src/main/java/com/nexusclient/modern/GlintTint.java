package com.nexusclient.modern;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;

/**
 * GlintTint – Enchant Glint Colour: recolours the shimmer on enchanted items and armour.
 *
 * The shimmer is a picture (texture) that Minecraft slides over enchanted things. When the mod is on,
 * we load Minecraft's own glint pictures, give every pixel the chosen colour (keeping how bright and
 * see-through it was), and swap them in (VersionHooks.tintTexture). Switching the mod off puts the
 * original pictures back. This only happens when the setting changes, never every frame.
 */
public final class GlintTint {

    /** The glint pictures – each version has some of these. */
    private static final String[] TEXTURES = {
            "textures/misc/enchanted_glint_item.png",
            "textures/misc/enchanted_glint_entity.png",
            "textures/misc/enchanted_glint_armor.png",
    };

    private static int applied = -1; // the colour now in use, or -1 = Minecraft's own glint

    private GlintTint() {}

    /** Called every tick: applies a colour change (or the mod switching on/off). */
    public static void tick() {
        Mods.EnchantGlint glint = NexusCore.get().get(Mods.EnchantGlint.class);
        int wanted = glint.isActive() ? glint.color.get() & 0xFFFFFF : -1;
        if (wanted == applied) {
            return;
        }
        for (String texture : TEXTURES) {
            if (wanted < 0) {
                VersionHooks.resetTexture(texture);
            } else {
                VersionHooks.tintTexture(texture, wanted);
            }
        }
        applied = wanted;
    }

    /** One pixel: keep its brightness and alpha, take the colour. Both ARGB/RGB. */
    public static int tint(int argb, int rgb) {
        int alpha = argb >>> 24;
        int brightness = Math.max((argb >> 16) & 255, Math.max((argb >> 8) & 255, argb & 255));
        int red = ((rgb >> 16) & 255) * brightness / 255;
        int green = ((rgb >> 8) & 255) * brightness / 255;
        int blue = (rgb & 255) * brightness / 255;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
