package com.nexusclient.legacy.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.GlStateManager;
import com.nexusclient.core.online.BadgeArt;
import com.nexusclient.core.online.Badges;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nexus badges in name tags, 1.8.9. This old version can't load fonts from mods, so instead:
 *   1. the name gets some spaces in front (and after, for the DEV tag) to make room
 *   2. once Minecraft has drawn the name, we draw the badge's pixels into that room as tiny squares
 *      (the same pixel art as the badge font on newer versions – see mod-core BadgeArt)
 *
 * In renderLabelIfPresent the name is drawn starting at x = -(width / 2), y = 0, 8 pixels tall.
 */
@Mixin(EntityRenderer.class)
public class BadgeMixin {

    private static final String BEFORE = "   ";    // 3 spaces = 12 pixels: room for the 8-pixel badge
    private static final String AFTER = "     ";   // 5 spaces = 20 pixels: room for the 16-pixel DEV tag

    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private String nexus$makeRoom(String text, @Local(argsOnly = true) Entity entity) {
        Badges.Badge badge = badgeFor(entity, text);
        if (badge == null) {
            return text;
        }
        return BEFORE + text + (badge == Badges.Badge.DEVELOPER ? AFTER : "");
    }

    @Inject(method = "renderLabelIfPresent",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;enableLighting()V"))
    private void nexus$drawBadge(Entity entity, String text, double x, double y, double z, int maxDistance,
                                 CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity) || !text.startsWith(BEFORE)) {
            return;
        }
        Badges.Badge badge = Badges.of(entity.getUuid());
        if (badge == null) {
            return;
        }
        int half = MinecraftClient.getInstance().textRenderer.getStringWidth(text) / 2;
        GlStateManager.disableTexture();
        draw(badge == Badges.Badge.PREMIUM ? BadgeArt.premium() : BadgeArt.nexus(), -half, 0);
        if (badge == Badges.Badge.DEVELOPER) {
            draw(BadgeArt.devTag(), half - 16, 0);
        }
        GlStateManager.enableTexture();
    }

    /** Which badge this name tag gets (null = none, or it's not a player's own name, e.g. their score). */
    private static Badges.Badge badgeFor(Entity entity, String text) {
        if (!(entity instanceof PlayerEntity) || !text.equals(entity.getName().asFormattedString())) {
            return null;
        }
        return Badges.of(entity.getUuid());
    }

    /** Draws pixel art at half size (16 art pixels = 8 name-tag pixels), one small square per pixel. */
    private static void draw(int[][] art, float left, float top) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, VertexFormats.POSITION_COLOR); // 7 = squares ("quads")
        for (int row = 0; row < art.length; row++) {
            for (int col = 0; col < art[row].length; col++) {
                int argb = art[row][col];
                if ((argb >>> 24) == 0) {
                    continue;
                }
                float r = ((argb >> 16) & 255) / 255f;
                float g = ((argb >> 8) & 255) / 255f;
                float b = (argb & 255) / 255f;
                double x1 = left + col * 0.5;
                double y1 = top + row * 0.5;
                double x2 = x1 + 0.5;
                double y2 = y1 + 0.5;
                buffer.vertex(x1, y1, -0.01).color(r, g, b, 1f).next();
                buffer.vertex(x1, y2, -0.01).color(r, g, b, 1f).next();
                buffer.vertex(x2, y2, -0.01).color(r, g, b, 1f).next();
                buffer.vertex(x2, y1, -0.01).color(r, g, b, 1f).next();
            }
        }
        tessellator.draw();
    }
}
