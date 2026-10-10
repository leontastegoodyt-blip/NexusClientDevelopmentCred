package com.nexusclient.launcher.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * DefaultSkin – the "Nexus default" character shown in the Skins preview before the player picks a
 * skin. It's drawn here pixel by pixel, because Nexus mustn't include Minecraft's own Steve skin.
 *
 * The look: brown hair, a proper face (eyes, nose, mouth), a dark navy hoodie with logo-blue trim and
 * a small glowing chest badge, dark trousers and grey-blue trainers. Every colour gets a tiny bit of
 * random-looking variation ("noise") so it looks like hand-made pixel art instead of flat paint.
 *
 * How a skin picture is laid out: every body part is a box, and its six sides are unfolded onto the
 * 64 x 64 picture like a paper model:
 *
 *            [ top ][bottom]
 *     [right][front][ left ][back]
 *
 * paintBox() fills those six areas using one small "painter" per part.
 */
public final class DefaultSkin {

    /** Which side of a box is being painted. */
    private enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    /** Decides the colour of one pixel on one side of a box (x, y counted from that side's corner). */
    private interface Painter {
        int colour(Face face, int x, int y, int width, int height);
    }

    // The palette.
    private static final int SKIN = 0xC69676;
    private static final int SKIN_DARK = 0xA87A5C;
    private static final int HAIR = 0x3A2416;
    private static final int HAIR_LIGHT = 0x4E3220;
    private static final int EYE_WHITE = 0xF2F2F2;
    private static final int EYE_BLUE = 0x3AA8FF;
    private static final int MOUTH = 0x6A3B28;
    private static final int HOODIE = 0x1B1E36;
    private static final int HOODIE_DARK = 0x14162A;
    private static final int TRIM = 0x3AA8FF;
    private static final int TRIM_LIGHT = 0x5FD3FF;
    private static final int TROUSERS = 0x262A44;
    private static final int SHOE = 0x8A93B8;
    private static final int SOLE = 0x3A3F5C;

    private DefaultSkin() {}

    public static Image create() {
        WritableImage skin = new WritableImage(64, 64); // starts fully see-through
        PixelWriter writer = skin.getPixelWriter();

        // Head: texture at (0, 0), 8 x 8 x 8.
        paintBox(writer, 0, 0, 8, 8, 8, (face, x, y, w, h) -> switch (face) {
            case TOP -> hairShade(x, y);
            case BOTTOM -> SKIN_DARK;
            case BACK -> y < 7 ? hairShade(x, y) : SKIN_DARK;
            case RIGHT, LEFT -> y < 3 || (y < 5 && (face == Face.RIGHT ? x < 3 : x > 4)) ? hairShade(x, y) : SKIN;
            case FRONT -> face(x, y);
        });

        // Body: texture at (16, 16), 8 x 12 x 4.
        paintBox(writer, 16, 16, 8, 12, 4, (face, x, y, w, h) -> {
            if (face == Face.FRONT) {
                if (y == 0 && x >= 2 && x <= 5) {
                    return y == 0 && (x == 2 || x == 5) ? TRIM : SKIN_DARK; // the neckline
                }
                if (y == 11) {
                    return TRIM;                                             // glowing hem
                }
                if (x == 5 && y == 3) {
                    return TRIM_LIGHT;                                       // chest badge
                }
                if ((x == 3 || x == 4) && y >= 1) {
                    return HOODIE_DARK;                                      // zip line
                }
                if (y >= 7 && y <= 9 && x >= 1 && x <= 6) {
                    return y == 7 ? HOODIE_DARK : HOODIE;                    // front pocket
                }
                return HOODIE;
            }
            if (face == Face.BACK) {
                return y < 3 ? HOODIE_DARK : (y == 11 ? TRIM : HOODIE);      // the hood hanging down
            }
            return y == h - 1 && face != Face.TOP && face != Face.BOTTOM ? TRIM : HOODIE;
        });

        // Arms: right at (40, 16), left at (32, 48), 4 x 12 x 4. Sleeve, blue cuff, then the hand.
        Painter arm = (face, x, y, w, h) -> {
            if (face == Face.TOP) {
                return HOODIE;
            }
            if (face == Face.BOTTOM) {
                return SKIN_DARK;
            }
            if (y < 8) {
                return y == 7 ? HOODIE_DARK : HOODIE;
            }
            if (y == 8) {
                return TRIM;
            }
            return y == 11 ? SKIN_DARK : SKIN;
        };
        paintBox(writer, 40, 16, 4, 12, 4, arm);
        paintBox(writer, 32, 48, 4, 12, 4, arm);

        // Legs: right at (0, 16), left at (16, 48), 4 x 12 x 4. Trousers, then trainers.
        Painter leg = (face, x, y, w, h) -> {
            if (face == Face.TOP) {
                return TROUSERS;
            }
            if (face == Face.BOTTOM) {
                return SOLE;
            }
            if (y >= 9) {
                return y == 11 ? SOLE : (y == 9 && face == Face.FRONT ? TRIM : SHOE);
            }
            return TROUSERS;
        };
        paintBox(writer, 0, 16, 4, 12, 4, leg);
        paintBox(writer, 16, 48, 4, 12, 4, leg);
        return skin;
    }

    /** The front of the head: hair fringe, eyes, nose and mouth. */
    private static int face(int x, int y) {
        if (y < 2 || (y == 2 && (x == 0 || x == 7 || x == 3))) {
            return hairShade(x, y);                 // fringe with a little parting
        }
        if (y == 4) {
            if (x == 1 || x == 6) {
                return EYE_WHITE;
            }
            if (x == 2 || x == 5) {
                return EYE_BLUE;
            }
        }
        if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) {
            return HAIR_LIGHT;                      // eyebrows
        }
        if (y == 5 && (x == 3 || x == 4)) {
            return SKIN_DARK;                       // nose
        }
        if (y == 6 && x >= 2 && x <= 5) {
            return MOUTH;
        }
        return SKIN;
    }

    private static int hairShade(int x, int y) {
        return ((x * 7 + y * 3) % 5 == 0) ? HAIR_LIGHT : HAIR;
    }

    /**
     * Paints one box's six sides into the skin picture.
     * (u, v) = top-left of the box's area in the picture; w, h, d = the box's width, height, depth.
     */
    private static void paintBox(PixelWriter writer, int u, int v, int w, int h, int d, Painter painter) {
        paintFace(writer, u + d, v, w, d, Face.TOP, painter);
        paintFace(writer, u + d + w, v, w, d, Face.BOTTOM, painter);
        paintFace(writer, u, v + d, d, h, Face.RIGHT, painter);
        paintFace(writer, u + d, v + d, w, h, Face.FRONT, painter);
        paintFace(writer, u + d + w, v + d, d, h, Face.LEFT, painter);
        paintFace(writer, u + d + w + d, v + d, w, h, Face.BACK, painter);
    }

    private static void paintFace(PixelWriter writer, int left, int top, int width, int height, Face face,
                                  Painter painter) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = painter.colour(face, x, y, width, height);
                writer.setArgb(left + x, top + y, 0xFF000000 | noise(rgb, left + x, top + y));
            }
        }
    }

    /** Nudges a colour slightly lighter or darker, the same way every time for the same pixel. */
    private static int noise(int rgb, int x, int y) {
        int n = ((x * 73856093) ^ (y * 19349663)) & 7;   // 0..7, looks random but never changes
        int shift = (n - 3) / 2;                          // -1..+2 (a gentle variation)
        int r = clamp(((rgb >> 16) & 0xFF) + shift * 2);
        int g = clamp(((rgb >> 8) & 0xFF) + shift * 2);
        int b = clamp((rgb & 0xFF) + shift * 2);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
