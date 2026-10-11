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

    /**
     * Draws a picture made of pixels, stretched over (x1, y1)–(x2, y2). Each loader keeps it on the
     * graphics card and only sends it again when {@code version} changes, so drawing a big picture
     * every frame costs about as much as one rectangle (the minimap uses this).
     *
     * @param id      a name for the picture – each name keeps its own copy on the graphics card
     * @param pixels  ARGB colours, row by row ({@code width} x {@code height})
     * @param version change this whenever the pixels change
     */
    default void picture(String id, int[] pixels, int width, int height, int version, int x1, int y1, int x2, int y2) {
        // Fallback (a loader without pictures): one rectangle per pixel – correct, but slow.
        float sx = (x2 - x1) / (float) width;
        float sy = (y2 - y1) / (float) height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rect(x1 + Math.round(x * sx), y1 + Math.round(y * sy), x1 + Math.round((x + 1) * sx),
                        y1 + Math.round((y + 1) * sy), pixels[y * width + x]);
            }
        }
    }

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
