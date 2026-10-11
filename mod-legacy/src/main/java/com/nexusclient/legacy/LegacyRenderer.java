package com.nexusclient.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import com.nexusclient.core.platform.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LegacyRenderer – draws mod-core's menus and HUD with Minecraft 1.8.9's old drawing tools
 * (DrawableHelper, TextRenderer and OpenGL calls through GlStateManager).
 *
 * 1.8.9 has no "graphics" object that remembers moves and zooms, so this class keeps its own list
 * of them (needed to work out where to cut off scrolling lists – see enableClip).
 */
public class LegacyRenderer implements Renderer {

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final TextRenderer font = mc.textRenderer;
    private final Window window = new Window(mc);

    /** The current move + zoom: {x, y, scale}. push() adds one, pop() removes one. */
    private final Deque<float[]> transforms = new ArrayDeque<float[]>();

    public LegacyRenderer() {
        transforms.push(new float[] {0, 0, 1});
    }

    @Override
    public int width() {
        return window.getWidth();
    }

    @Override
    public int height() {
        return window.getHeight();
    }

    @Override
    public void rect(int x1, int y1, int x2, int y2, int argb) {
        DrawableHelper.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void outline(int x1, int y1, int x2, int y2, int argb) {
        DrawableHelper.fill(x1, y1, x2, y1 + 1, argb);     // top
        DrawableHelper.fill(x1, y2 - 1, x2, y2, argb);     // bottom
        DrawableHelper.fill(x1, y1 + 1, x1 + 1, y2 - 1, argb); // left
        DrawableHelper.fill(x2 - 1, y1 + 1, x2, y2 - 1, argb); // right
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        // Text can be see-through, so blending must be on (1.8.9 doesn't switch it on for text).
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        font.draw(text, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String text) {
        return font.getStringWidth(text);
    }

    @Override
    public int lineHeight() {
        return font.fontHeight;
    }

    @Override
    public void item(Object item, int x, int y) {
        if (!(item instanceof ItemStack)) {
            return;
        }
        // The same steps 1.8.9's hotbar uses to draw items lit up properly.
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        DiffuseLighting.enable();
        mc.getItemRenderer().renderInGuiWithOverrides((ItemStack) item, x, y);
        DiffuseLighting.disable();
        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** A picture kept on the graphics card (see picture()). */
    private static final class Picture {
        net.minecraft.client.texture.NativeImageBackedTexture texture;
        net.minecraft.util.Identifier location;
        int width;
        int height;
        int version = Integer.MIN_VALUE;
    }

    private static final java.util.Map<String, Picture> PICTURES = new java.util.HashMap<String, Picture>();

    @Override
    public void picture(String id, int[] pixels, int width, int height, int version, int x1, int y1, int x2, int y2) {
        Picture picture = PICTURES.get(id);
        if (picture == null) {
            picture = new Picture();
            PICTURES.put(id, picture);
        }
        if (picture.texture == null || picture.width != width || picture.height != height) {
            picture.texture = new net.minecraft.client.texture.NativeImageBackedTexture(width, height);
            picture.location = new net.minecraft.util.Identifier("nexusclient", "picture/" + id);
            mc.getTextureManager().loadTexture(picture.location, picture.texture);
            picture.width = width;
            picture.height = height;
            picture.version = Integer.MIN_VALUE;
        }
        if (picture.version != version) {
            // Only sent to the graphics card when the pixels changed.
            System.arraycopy(pixels, 0, picture.texture.getPixels(), 0, width * height);
            picture.texture.upload();
            picture.version = version;
        }
        mc.getTextureManager().bindTexture(picture.location);
        GlStateManager.enableTexture();
        GlStateManager.color(1, 1, 1, 1);
        DrawableHelper.drawTexture(x1, y1, 0, 0, width, height, x2 - x1, y2 - y1, width, height);
    }

    @Override
    public void push(float x, float y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        float[] current = transforms.peek();
        transforms.push(new float[] {current[0] + x * current[2], current[1] + y * current[2], current[2] * scale});
    }

    @Override
    public void pop() {
        GlStateManager.popMatrix();
        if (transforms.size() > 1) {
            transforms.pop();
        }
    }

    @Override
    public void enableClip(int x1, int y1, int x2, int y2) {
        // OpenGL's "scissor" cuts drawing to a box in real window pixels, counted from the bottom.
        float[] t = transforms.peek();
        int factor = window.getScaleFactor();
        int left = Math.round((t[0] + x1 * t[2]) * factor);
        int right = Math.round((t[0] + x2 * t[2]) * factor);
        int top = Math.round((t[1] + y1 * t[2]) * factor);
        int bottom = Math.round((t[1] + y2 * t[2]) * factor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, mc.height - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    @Override
    public void disableClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
