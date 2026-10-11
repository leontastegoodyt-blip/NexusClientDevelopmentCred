package com.nexusclient.modern;

import com.nexusclient.core.platform.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * FabricRenderer – draws mod-core's menus and HUD with Minecraft 1.20–1.21's drawing tool
 * (GuiGraphics). A new one is made for each frame, wrapping that frame's graphics.
 * Moving/zooming works differently from 1.21.6 on, so that part is in GameHooks.
 */
public class FabricRenderer implements Renderer {

    private final GuiGraphics graphics;
    private final Font font;

    public FabricRenderer(GuiGraphics graphics) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public void rect(int x1, int y1, int x2, int y2, int argb) {
        graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void outline(int x1, int y1, int x2, int y2, int argb) {
        graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        graphics.drawString(font, text, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void item(Object item, int x, int y) {
        if (item instanceof ItemStack stack) {
            graphics.renderItem(stack, x, y);
        }
    }

    @Override
    public void picture(String id, int[] pixels, int width, int height, int version, int x1, int y1, int x2, int y2) {
        VersionHooks.picture(graphics, id, pixels, width, height, version, x1, y1, x2, y2);
    }

    @Override
    public void push(float x, float y, float scale) {
        GameHooks.push(graphics, x, y, scale);
    }

    @Override
    public void pop() {
        GameHooks.pop(graphics);
    }

    @Override
    public void enableClip(int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void disableClip() {
        graphics.disableScissor();
    }
}
