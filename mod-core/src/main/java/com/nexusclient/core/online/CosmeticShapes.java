package com.nexusclient.core.online;

/**
 * CosmeticShapes – the Nexus hats and wings, built from glowing shapes in code (no 3D model files).
 *
 * Every Minecraft version draws them the same way: it moves to the player's head (for the Halo) or
 * body (for the Neon Wings), then calls halo() or wings() with a Sink that turns each corner into a
 * vertex in its own graphics code. Shapes are made of four-corner pieces ("quads"); a triangle is a
 * quad whose last two corners are the same.
 *
 * Positions are in Minecraft's model units (16 = one block), relative to the head or body joint:
 *   x = sideways, y = DOWN (Minecraft models are upside down), z = backwards (the back is +z).
 * The top of the head is at y = -8; the body goes from y = 0 (neck) to y = 12 (hips), back at z = 2.
 *
 * Colours follow the Nexus logo: light blue → logo blue → violet.
 */
public final class CosmeticShapes {

    /** Receives the corners of the shapes. 4 calls = one quad. Colour is 0xAARRGGBB. */
    public interface Sink {
        void vertex(float x, float y, float z, int argb);
    }

    private static final int LIGHT_BLUE = 0x5FD3FF;
    private static final int LOGO_BLUE = 0x3AA8FF;
    private static final int VIOLET = 0x8B5CFF;

    private CosmeticShapes() {}

    // ------------------------------------------------------------------ Halo

    /**
     * The Halo: a glowing ring floating above the head, gently bobbing, with its colours slowly
     * running around it. Draw it in head space.
     *
     * @param ticks the player's age in ticks (with the partial tick), for the animation
     */
    public static void halo(Sink sink, float ticks) {
        final int segments = 32;
        float y = -11.0f + (float) Math.sin(ticks * 0.08) * 0.35f; // top surface height (bobbing)
        float inner = 3.3f;
        float outer = 4.3f;
        float thick = 0.7f;
        float spin = ticks * 0.004f;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            int col0 = 0xFF000000 | loop((float) i / segments + spin);
            int col1 = 0xFF000000 | loop((float) (i + 1) / segments + spin);
            // Top and bottom faces of the band.
            for (float face : new float[] {y, y + thick}) {
                sink.vertex(c0 * inner, face, s0 * inner, col0);
                sink.vertex(c0 * outer, face, s0 * outer, col0);
                sink.vertex(c1 * outer, face, s1 * outer, col1);
                sink.vertex(c1 * inner, face, s1 * inner, col1);
            }
            // Outer and inner walls, so the ring is solid when seen from the side.
            for (float r : new float[] {inner, outer}) {
                sink.vertex(c0 * r, y, s0 * r, col0);
                sink.vertex(c1 * r, y, s1 * r, col1);
                sink.vertex(c1 * r, y + thick, s1 * r, col1);
                sink.vertex(c0 * r, y + thick, s0 * r, col0);
            }
            // Soft glow: see-through rings fading out on both sides of the band.
            float mid = y + thick / 2;
            int glow0 = loop((float) i / segments + spin);
            int glow1 = loop((float) (i + 1) / segments + spin);
            fadeRing(sink, c0, s0, c1, s1, outer, outer + 1.8f, mid, glow0, glow1, 0x70, 0x00);
            fadeRing(sink, c0, s0, c1, s1, inner - 1.4f, inner, mid, glow0, glow1, 0x00, 0x70);
        }
    }

    /** A flat ring piece from radius r0 (alpha a0) to r1 (alpha a1). */
    private static void fadeRing(Sink sink, float c0, float s0, float c1, float s1, float r0, float r1,
                                 float y, int rgb0, int rgb1, int a0, int a1) {
        sink.vertex(c0 * r0, y, s0 * r0, (a0 << 24) | rgb0);
        sink.vertex(c0 * r1, y, s0 * r1, (a1 << 24) | rgb0);
        sink.vertex(c1 * r1, y, s1 * r1, (a1 << 24) | rgb1);
        sink.vertex(c1 * r0, y, s1 * r0, (a0 << 24) | rgb1);
    }

    // ------------------------------------------------------------------ Wings

    /*
     * One wing's outline, drawn flat: {outward, up/down}. Starts at the top of the root, runs along
     * the top edge to the tip, then zig-zags back along the feather tips to the bottom of the root.
     */
    private static final float[][] OUTLINE = {
            {0, -1}, {3.5f, -4.5f}, {8, -7.5f}, {13.5f, -9.5f},  // top edge up to the tip
            {11.5f, -5.5f}, {13.5f, -3}, {10, -1.5f},             // feather 1
            {11.5f, 2}, {7.5f, 1}, {8.5f, 5}, {4.5f, 2.5f},       // feathers 2 and 3
            {4, 5.5f}, {0, 2.5f}                                   // feather 4, back to the root
    };
    /** Where the "bones" run to: the tip and each feather point. */
    private static final int[] RIBS = {3, 5, 7, 9, 11};
    private static final float ROOT_U = 0, ROOT_V = 0.75f;
    private static final float WING_SCALE = 1.1f;

    /**
     * The Neon Wings: two see-through wings with bright neon edges and bones, slowly flapping.
     * Draw them in body space.
     *
     * @param ticks  the player's age in ticks (with the partial tick)
     * @param flying true while the player is in the air or flying: the wings flap faster and wider
     */
    public static void wings(Sink sink, float ticks, boolean flying) {
        double flap = flying ? Math.sin(ticks * 0.45) * 0.45 : Math.sin(ticks * 0.1) * 0.18;
        float angle = (float) (0.4 + flap); // 0 = spread flat sideways, bigger = swept back
        for (int side = -1; side <= 1; side += 2) {
            Wing wing = new Wing(sink, side, angle);
            // 1) the see-through fill: a fan of triangles from the root
            for (int i = 0; i + 1 < OUTLINE.length; i++) {
                wing.triangle(ROOT_U, ROOT_V, OUTLINE[i][0], OUTLINE[i][1], OUTLINE[i + 1][0], OUTLINE[i + 1][1], 0x58);
            }
            // 2) a wide faint glow along the edge, then 3) the bright edge itself
            for (int i = 0; i + 1 < OUTLINE.length; i++) {
                wing.line(OUTLINE[i][0], OUTLINE[i][1], OUTLINE[i + 1][0], OUTLINE[i + 1][1], 1.6f, 0x38);
            }
            for (int i = 0; i + 1 < OUTLINE.length; i++) {
                wing.line(OUTLINE[i][0], OUTLINE[i][1], OUTLINE[i + 1][0], OUTLINE[i + 1][1], 0.45f, 0xFF);
            }
            // 4) the bones from the root to every feather point
            for (int rib : RIBS) {
                wing.line(ROOT_U, ROOT_V, OUTLINE[rib][0], OUTLINE[rib][1], 0.3f, 0xB0);
            }
        }
    }

    /** Turns a point on the flat wing outline into a point in body space, for one side. */
    private static final class Wing {
        private final Sink sink;
        private final int side;
        private final float cos;
        private final float sin;

        Wing(Sink sink, int side, float angle) {
            this.sink = sink;
            this.side = side;
            this.cos = (float) Math.cos(angle);
            this.sin = (float) Math.sin(angle);
        }

        private void point(float u, float v, int alpha) {
            u *= WING_SCALE;
            v *= WING_SCALE;
            float x = side * (1.2f + u * cos);
            float y = 3.0f + v;
            float z = 2.3f + u * sin;
            sink.vertex(x, y, z, (alpha << 24) | ramp(u / (13.5f * WING_SCALE)));
        }

        void triangle(float u0, float v0, float u1, float v1, float u2, float v2, int alpha) {
            point(u0, v0, alpha);
            point(u1, v1, alpha);
            point(u2, v2, alpha);
            point(u2, v2, alpha);
        }

        /** A straight band of the given width from (u0, v0) to (u1, v1), lying in the wing. */
        void line(float u0, float v0, float u1, float v1, float width, int alpha) {
            float du = u1 - u0;
            float dv = v1 - v0;
            float length = (float) Math.sqrt(du * du + dv * dv);
            if (length < 0.0001f) {
                return;
            }
            float nu = -dv / length * width / 2;
            float nv = du / length * width / 2;
            point(u0 - nu, v0 - nv, alpha);
            point(u1 - nu, v1 - nv, alpha);
            point(u1 + nu, v1 + nv, alpha);
            point(u0 + nu, v0 + nv, alpha);
        }
    }

    // ------------------------------------------------------------------ Colours

    /** 0 → light blue, 0.5 → logo blue, 1 → violet. */
    static int ramp(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? mix(LIGHT_BLUE, LOGO_BLUE, t * 2) : mix(LOGO_BLUE, VIOLET, (t - 0.5f) * 2);
    }

    /** Like ramp(), but loops back to light blue (for things that go round, like the halo). */
    static int loop(float t) {
        t = t - (float) Math.floor(t);
        return t < 0.5f ? ramp(t * 2) : ramp((1 - t) * 2);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
