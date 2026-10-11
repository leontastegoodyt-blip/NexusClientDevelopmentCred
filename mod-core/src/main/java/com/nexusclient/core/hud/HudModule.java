package com.nexusclient.core.hud;

import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ColorSetting;
import com.nexusclient.core.settings.NumberSetting;

/**
 * HudModule – a module that draws something on the screen while playing (the HUD), like the FPS
 * counter or keystrokes. It can be dragged around and scaled in the HUD editor.
 *
 * Its position is stored as a fraction of the screen (0.0 = left/top edge, 1.0 = right/bottom edge),
 * so it stays in the same place on any screen size.
 */
public abstract class HudModule extends Module {

    // Where it is and how big (changed by dragging and scrolling in the HUD editor).
    private double x;
    private double y;
    public final NumberSetting scale;

    // How it looks (the project brief: change colours, background, text shadow, and a neon/chroma option).
    public final ColorSetting textColor;
    public final BooleanSetting background;
    public final ColorSetting backgroundColor;
    public final BooleanSetting textShadow;

    // Its size last time it was drawn (in screen pixels, after scaling) – used by the HUD editor.
    private int lastWidth;
    private int lastHeight;

    protected HudModule(String id, String name, String description, boolean enabledByDefault,
                        double defaultX, double defaultY) {
        this(id, name, description, Category.HUD, enabledByDefault, defaultX, defaultY);
    }

    /** A HUD module that belongs to another category (e.g. the developer's debug HUD). */
    protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault,
                        double defaultX, double defaultY) {
        this(id, name, description, category, enabledByDefault, false, defaultX, defaultY);
    }

    /** …and one that servers may switch off (e.g. the minimap). */
    protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault,
                        boolean serverBlockable, double defaultX, double defaultY) {
        super(id, name, description, category, enabledByDefault, serverBlockable);
        this.x = defaultX;
        this.y = defaultY;
        scale = add(new NumberSetting("scale", "Scale", 1.0, 0.5, 3.0, 0.1, "x"));
        textColor = add(new ColorSetting("textColor", "Text colour", 0xFFFFFFFF));
        background = add(new BooleanSetting("background", "Background", true));
        backgroundColor = add(new ColorSetting("backgroundColor", "Background colour", 0x90101018));
        textShadow = add(new BooleanSetting("textShadow", "Text shadow", true));
    }

    /**
     * Draws the module with its top-left corner at (0, 0) and returns its size (unscaled).
     *
     * @param preview true in the HUD editor when not in a world – show example values instead
     * @return {width, height}
     */
    protected abstract int[] draw(Renderer renderer, GameInfo game, boolean preview);

    /** Draws the module at its saved position and scale. */
    public void render(Renderer renderer, GameInfo game, boolean preview) {
        float s = (float) scale.get().doubleValue();
        int px = (int) Math.round(x * renderer.width());
        int py = (int) Math.round(y * renderer.height());
        // Keep it on screen even if the window got smaller.
        px = Math.max(0, Math.min(px, renderer.width() - lastWidth));
        py = Math.max(0, Math.min(py, renderer.height() - lastHeight));
        renderer.push(px, py, s);
        int[] size = draw(renderer, game, preview);
        renderer.pop();
        lastWidth = Math.round(size[0] * s);
        lastHeight = Math.round(size[1] * s);
    }

    // ------------------------------------------------------------------ position (for the HUD editor)

    public double x() { return x; }
    public double y() { return y; }

    public void setPosition(double x, double y) {
        this.x = Math.max(0, Math.min(1, x));
        this.y = Math.max(0, Math.min(1, y));
    }

    /** The top-left corner on this screen, in pixels. */
    public int pixelX(Renderer renderer) {
        return Math.max(0, Math.min((int) Math.round(x * renderer.width()), renderer.width() - lastWidth));
    }

    public int pixelY(Renderer renderer) {
        return Math.max(0, Math.min((int) Math.round(y * renderer.height()), renderer.height() - lastHeight));
    }

    public int width() { return Math.max(lastWidth, 8); }
    public int height() { return Math.max(lastHeight, 8); }

    // ------------------------------------------------------------------ drawing helpers

    /** Draws the background box (if switched on). */
    protected void drawBackground(Renderer renderer, int width, int height) {
        if (background.isOn()) {
            renderer.rect(0, 0, width, height, backgroundColor.argb());
        }
    }

    /** Draws text in the module's colour (chroma shifts along the text). */
    protected void drawText(Renderer renderer, String text, int x, int y) {
        renderer.text(text, x, y, textColor.argbAt(x + (int) Math.round(this.x * 1000)), textShadow.isOn());
    }
}
