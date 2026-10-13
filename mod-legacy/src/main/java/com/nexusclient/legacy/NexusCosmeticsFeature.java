package com.nexusclient.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import com.nexusclient.core.online.Capes;
import com.nexusclient.core.online.CosmeticShapes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Draws the Nexus Halo and Neon Wings on players who wear them (1.8.9). The shapes come from mod-core
 * CosmeticShapes. 1.8.9 has no shaders here, so it's plain old OpenGL: no texture, no lighting, full
 * brightness, see-through, and both sides of every shape visible.
 */
public class NexusCosmeticsFeature implements FeatureRenderer<AbstractClientPlayerEntity> {

    private final PlayerEntityRenderer renderer;

    public NexusCosmeticsFeature(PlayerEntityRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void render(AbstractClientPlayerEntity player, float handSwing, float handSwingAmount, float tickDelta,
                       float age, float headYaw, float headPitch, float scale) {
        if (player.isInvisible()) {
            return;
        }
        String hat = Capes.hatOf(player.getUuid());
        String wings = Capes.wingsOf(player.getUuid());
        if (hat == null && wings == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlphaTest(); // otherwise the faint glow would be cut away
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH); // colours blend smoothly across each shape
        mc.gameRenderer.disableLightmap();
        if (hat != null) {
            GlStateManager.pushMatrix();
            renderer.getModel().head.preRender(scale); // move to the head (follows where it looks)
            draw(sink -> CosmeticShapes.halo(sink, age), scale);
            GlStateManager.popMatrix();
        }
        if (wings != null) {
            GlStateManager.pushMatrix();
            renderer.getModel().body.preRender(scale); // move to the body (follows sneaking)
            draw(sink -> CosmeticShapes.wings(sink, age, !player.onGround), scale);
            GlStateManager.popMatrix();
        }
        mc.gameRenderer.enableLightmap();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlphaTest();
        GlStateManager.enableCull();
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }

    /** One shape, sent to the graphics card as quads (CosmeticShapes units × the model scale). */
    private static void draw(java.util.function.Consumer<CosmeticShapes.Sink> shape, float scale) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, VertexFormats.POSITION_COLOR); // 7 = squares ("quads")
        shape.accept((x, y, z, argb) -> buffer.vertex(x * scale, y * scale, z * scale)
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).next());
        tessellator.draw();
    }

    @Override
    public boolean combineTextures() {
        return false;
    }
}
