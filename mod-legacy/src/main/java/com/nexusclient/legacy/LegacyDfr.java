package com.nexusclient.legacy;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.dfr.Dfr;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * LegacyDfr – DFR (Dual Frame Reset) and Motion Blur on 1.8.9 (see core/dfr/Dfr).
 *
 * 1.8.9 uses very old OpenGL, so this needs no shaders at all: it copies each finished frame into a
 * picture (texture) and makes the in-between frame by drawing pictures on top of each other, see-through:
 *   Smooth      – before real frame N is shown: show N drawn half see-through over N-1, then N
 *   Low lag     – after real frame N is shown: show N again, slid by the mouse movement that arrived
 *                 since (the movement is "borrowed" and handed back to the game – see MouseInputMixin)
 *   Motion Blur – the world (not the HUD) mixed with a 16-bit "trail" of the frames before (blurWorld)
 *
 * Hooked around MinecraftClient.updateDisplay (DfrMixin), where the frame is swapped onto the screen.
 * Every OpenGL setting is saved first (glPushAttrib + the matrix stacks) and put back after, so
 * Minecraft's own record of the settings stays right.
 */
public final class LegacyDfr {

    private static int newest;   // texture: the frame Minecraft just finished
    private static int older;    // texture: the frame before it (or, for Motion Blur, the picture shown last)
    private static int width;
    private static int height;
    private static boolean havePrevious;
    private static boolean blurHasPrevious;
    private static boolean failed;
    private static long slowSince;
    /** Mouse movement read early by Low lag, handed back to the game on its next mouse update. */
    private static int borrowedX;
    private static int borrowedY;

    private LegacyDfr() {}

    /** Called just before the finished frame is swapped onto the screen. */
    public static void beforeShow() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (failed || mc.world == null) {
            havePrevious = false;
            return;
        }
        Dfr dfr = NexusCore.get().get(Dfr.class);
        if (!shouldRun(dfr)) {
            havePrevious = false;
            return;
        }
        if (mc.width <= 0 || mc.height <= 0 || !onScreen()) {
            return;
        }
        save();
        try {
            ensureTextures(mc.width, mc.height);
            grab(newest);
            if (!dfr.lowLag() && havePrevious && worthGenerating()) {
                draw(older, 0, 0, 1);
                draw(newest, 0, 0, 1 - 0.5f * dfr.effectiveStrength());
                Display.swapBuffers(); // the in-between frame…
                draw(newest, 0, 0, 1);  // …then the real one goes back for Minecraft to show
                dfr.frameGenerated();
            }
            int swap = newest;
            newest = older;
            older = swap; // the frame just finished is "the one before" next time
            havePrevious = true;
        } catch (Exception e) {
            giveUp(e);
        } finally {
            restore();
        }
    }

    // ------------------------------------------------------------------ Motion Blur

    private static int trail;     // 16-bit colour picture: the world mixed with the frames before
    private static int trailFbo;
    private static int trailWidth;
    private static int trailHeight;

    /**
     * Motion Blur, called when Minecraft has drawn the world but not the HUD yet (MotionBlurMixin), so
     * only the world blurs – never the hotbar, chat or text. The world is laid see-through over a
     * "trail" picture kept in 16-bit colour (with normal 8-bit colour the tiny changes at high FPS
     * round away and leave ghost images that never fade), then the trail is copied back.
     */
    public static void blurWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        NexusCore core = NexusCore.get();
        Mods.MotionBlur blur = core.get(Mods.MotionBlur.class);
        net.minecraft.client.gl.Framebuffer world = mc.getFramebuffer();
        if (failed || !blur.isActive() || core.get(Dfr.class).isActive() || mc.world == null
                || world == null || world.fbo <= 0 || !GLContext.getCapabilities().OpenGL30) {
            blurHasPrevious = false;
            return;
        }
        int w = world.viewportWidth;
        int h = world.viewportHeight;
        float u = w / (float) world.textureWidth;
        float v = h / (float) world.textureHeight;
        save();
        try {
            if (trail == 0 || trailWidth != w || trailHeight != h) {
                if (trail != 0) {
                    GL30.glDeleteFramebuffers(trailFbo);
                    GL11.glDeleteTextures(trail);
                }
                trail = texture(w, h, GL30.GL_RGBA16F, GL11.GL_FLOAT);
                trailFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, trailFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, trail, 0);
                trailWidth = w;
                trailHeight = h;
                blurHasPrevious = false;
            }
            GL11.glViewport(0, 0, w, h);
            // 1. The world, see-through, onto the trail (the first time: solid, so the trail starts as it).
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, trailFbo);
            drawPart(world.colorAttachment, u, v, blurHasPrevious ? 1 - blur.keepThisFrame() : 1);
            blurHasPrevious = true;
            // 2. The trail back into the world picture.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, world.fbo);
            drawPart(trail, 1, 1, 1);
        } catch (Exception e) {
            giveUp(e);
        } finally {
            restore();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, world.fbo); // Minecraft keeps drawing the HUD into it
        }
    }

    /** Called just after the swap (Low lag mode works here). */
    public static void afterShow() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (failed || !havePrevious || mc.world == null) {
            return;
        }
        Dfr dfr = NexusCore.get().get(Dfr.class);
        if (!dfr.isActive() || !dfr.lowLag() || !worthGenerating() || !onScreen()) {
            return;
        }
        float shiftX = 0;
        float shiftY = 0;
        if (mc.currentScreen == null && Mouse.isGrabbed()) {
            // Read the mouse movement now; the game gets it back on its next update.
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();
            borrowedX += dx;
            borrowedY += dy;
            float s = mc.options.sensitivity * 0.6f + 0.2f;
            float factor = s * s * s * 8.0f * 0.15f; // mouse movement → degrees (as in 1.8.9 itself)
            float verticalFov = mc.options.fov;
            float aspect = (float) width / Math.max(1, height);
            double horizontalFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(verticalFov / 2)) * aspect));
            shiftX = (float) Math.max(-0.45, Math.min(0.45, dx * factor / horizontalFov));
            shiftY = Math.max(-0.45f, Math.min(0.45f, dy * factor / verticalFov * (mc.options.invertYMouse ? -1 : 1)));
        }
        float strength = dfr.effectiveStrength();
        save();
        try {
            draw(older, shiftX * strength, shiftY * strength, 1); // after the swap, "older" is the frame just shown
            Display.swapBuffers();
            dfr.frameGenerated();
        } catch (Exception e) {
            giveUp(e);
        } finally {
            restore();
        }
    }

    /** MouseInputMixin: adds back the movement Low lag read early. */
    public static int[] takeBorrowedMouse() {
        int[] taken = {borrowedX, borrowedY};
        borrowedX = 0;
        borrowedY = 0;
        return taken;
    }

    // ------------------------------------------------------------------ rules

    private static boolean shouldRun(Dfr dfr) {
        if (!dfr.isActive()) {
            return false;
        }
        // Too slow for DFR: under 20 real FPS for 10 seconds → switch off and say why.
        if (MinecraftClient.getCurrentFps() < 20) {
            if (slowSince == 0) {
                slowSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - slowSince > 10_000) {
                dfr.setEnabled(false);
                slowSince = 0;
                NexusCore.get().noticeLater("DFR switched off: this PC is running under 20 FPS, which is too slow for "
                        + "DFR to help. Try TorcherRig's settings first.");
                return false;
            }
        } else {
            slowSince = 0;
        }
        return true;
    }

    /** Extra frames only help if the game runs slower than half the monitor's refresh rate. */
    private static boolean worthGenerating() {
        if (Boolean.getBoolean("nexus.dfr.force")) {
            return true; // developer self-test: always generate
        }
        int refresh = Display.getDesktopDisplayMode().getFrequency();
        if (refresh <= 0) {
            refresh = 60;
        }
        return MinecraftClient.getCurrentFps() * 2 <= refresh * 1.1f;
    }

    /** Only when Minecraft drew straight to the window (no off-screen framebuffer still bound). */
    private static boolean onScreen() {
        return !GLContext.getCapabilities().OpenGL30 || GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == 0;
    }

    // ------------------------------------------------------------------ OpenGL

    private static int program;

    /** Saves every OpenGL setting, then sets up plain flat drawing over the whole window. */
    private static void save() {
        program = GLContext.getCapabilities().OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        for (int matrix : new int[] {GL11.GL_PROJECTION, GL11.GL_MODELVIEW, GL11.GL_TEXTURE}) {
            GL11.glMatrixMode(matrix);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glOrtho(0, 1, 0, 1, -1, 1); // the window becomes a flat 0–1 square
        if (program != 0) {
            GL20.glUseProgram(0);
        }
        // Texture unit 1 holds Minecraft's light map – switch it off for our drawing.
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (onScreen()) { // (choosing the back buffer is an error while a framebuffer is in use)
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glDrawBuffer(GL11.GL_BACK);
        }
    }

    private static void restore() {
        for (int matrix : new int[] {GL11.GL_TEXTURE, GL11.GL_MODELVIEW, GL11.GL_PROJECTION}) {
            GL11.glMatrixMode(matrix);
            GL11.glPopMatrix();
        }
        GL11.glPopAttrib(); // also puts back the matrix mode, texture bindings, colours and switches
        if (program != 0) {
            GL20.glUseProgram(program);
        }
    }

    /** Copies what's in the window right now into a texture. */
    private static void grab(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    /** Draws part of a texture (0–u across, 0–v down) over the whole drawing area, with the given opacity. */
    private static void drawPart(int texture, float u, float v, float alpha) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (alpha < 1) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glColor4f(1, 1, 1, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(u, 0);
        GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, v);
        GL11.glVertex2f(0, 1);
        GL11.glEnd();
    }

    /** Draws a texture over the whole window, slid by (shiftX, shiftY), with the given opacity. */
    private static void draw(int texture, float shiftX, float shiftY, float alpha) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (alpha < 1) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glColor4f(1, 1, 1, alpha);
        // Sliding the picture coordinates moves the image; the edge pixels stretch to fill the gap.
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(shiftX, shiftY);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1 + shiftX, shiftY);
        GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1 + shiftX, 1 + shiftY);
        GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(shiftX, 1 + shiftY);
        GL11.glVertex2f(0, 1);
        GL11.glEnd();
    }

    private static void ensureTextures(int w, int h) {
        if (newest != 0 && w == width && h == height) {
            return;
        }
        if (newest != 0) {
            GL11.glDeleteTextures(newest);
            GL11.glDeleteTextures(older);
        }
        width = w;
        height = h;
        newest = texture(w, h);
        older = texture(w, h);
        havePrevious = blurHasPrevious = false;
    }

    private static int texture(int w, int h) {
        return texture(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
    }

    /** A picture on the graphics card: GL_RGBA8 (normal colour) or GL_RGBA16F (precise, for the blur trail). */
    private static int texture(int w, int h, int format, int type) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F); // clamp to edge
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        return id;
    }

    private static void giveUp(Exception e) {
        failed = true;
        System.err.println("[Nexus] DFR stopped: " + e);
        NexusCore.get().noticeLater("DFR couldn't run on this graphics card and was switched off.");
    }
}
