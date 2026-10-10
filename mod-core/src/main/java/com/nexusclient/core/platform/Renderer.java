package com.nexusclient.core.platform;

/**
 * Renderer – how mod-core draws on screen. Each loader module (Fabric, Forge, 1.8.9) implements this
 * with that version of Minecraft's own drawing code, so the menus and HUD look the same everywhere.
 *
 * Coordinates are in "GUI pixels" (Minecraft's scaled screen units). Colours are ARGB:
 * 0xAARRGGBB, e.g. 0xFF3AA8FF = solid Nexus blue, 0x80000000 = half-see-through black.
 */
public interface Renderer {

    /** The screen size in GUI pixels. */
    int width();

    int height();

    /** A filled rectangle from (x1, y1) to (x2, y2). */
    void rect(int x1, int y1, int x2, int y2, int argb);

    /** A 1-pixel outline around a rectangle. */
    void outline(int x1, int y1, int x2, int y2, int argb);

    /** Text with its top-left corner at (x, y). */
    void text(String text, int x, int y, int argb, boolean shadow);

    /** How wide some text is, in GUI pixels. */
    int textWidth(String text);

    /** How tall one line of text is. */
    int lineHeight();

    /**
     * Draws a Minecraft item (an icon from the inventory) at (x, y), 16 x 16 pixels.
     *
     * @param item the platform's own item object (from GameInfo's item lists)
     */
    void item(Object item, int x, int y);

    /** Starts drawing moved to (x, y) and scaled – everything until pop() is affected. */
    void push(float x, float y, float scale);

    /** Undoes the last push(). */
    void pop();

    /**
     * Only draws inside this rectangle until disableClip() (used for scrolling lists).
     * The rectangle is in the current drawing space (so it moves and scales with push()).
     */
    void enableClip(int x1, int y1, int x2, int y2);

    void disableClip();
}
