package com.nexusclient.fabric;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.dfr.Dfr;
import com.nexusclient.core.module.Mods;
import com.nexusclient.fabric.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * DfrRenderer for 26.1 – DFR (Dual Frame Reset), see core/dfr/Dfr. The same code as 1.20–1.21.11's
 * ModernDfr (mod-fabric-modern): 26.1 still draws with plain OpenGL, unlike 26.2+ (see mod-fabric/src).
 *
 * Each finished frame sits in the window's hidden "back buffer" until Minecraft shows it
 * (RenderSystem.flipFrame). DfrMixin calls in here just before and just after that:
 *   Smooth  – before real frame N is shown: show blend(N-1, N) first, then put N back and let it show
 *   Low lag – after real frame N is shown: show N again, shifted by the mouse movement that arrived
 *             since (camera prediction)
 * The in-between frame is drawn by a tiny OpenGL program (the shaders are the strings below).
 *
 * The same shader also makes Motion Blur (blurWorld): the world – not the HUD – is mixed with a
 * 16-bit "trail" of the frames before (MotionBlur.keepThisFrame keeps it the same length at any FPS).
 *
 * Minecraft remembers which OpenGL settings it last used, so everything changed here is put back
 * exactly as it was (GlState) – otherwise Minecraft's next frame would draw wrongly.
 *
 * Rules (the project brief): no extra frames when the game is already faster than half the monitor's refresh
 * rate (they'd never be seen), and DFR switches off with a message under 20 FPS for 10 seconds.
 */
public final class DfrRenderer {

    private static final String VERTEX = """
            #version 150
            out vec2 uv;
            void main() {
                // One triangle that covers the whole screen, made from the vertex number alone.
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = corner;
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D Current;   // the newest real frame
            uniform sampler2D Previous;  // the frame before it (or, for Motion Blur, the picture shown last)
            uniform int Mode;            // 0 = Smooth blend, 1 = Low lag shift, 2 = Motion Blur
            uniform float Strength;      // 0-1
            uniform vec2 Shift;          // Low lag: how far the camera has already turned (part of the screen)
            in vec2 uv;
            out vec4 colour;
            void main() {
                if (Mode == 0) {
                    colour = mix(texture(Current, uv), texture(Previous, uv), 0.5 * Strength);
                } else if (Mode == 2) {
                    colour = mix(texture(Current, uv), texture(Previous, uv), Strength);
                } else {
                    colour = texture(Current, clamp(uv + Shift * Strength, 0.0, 1.0));
                }
                colour.a = 1.0;
            }
            """;

    private static int program;
    private static int vao;
    private static int uMode;
    private static int uStrength;
    private static int uShift;
    /** Two pictures the size of the window: the newest frame and the one before (they swap roles). */
    private static int newestTexture;
    private static int newestFbo;
    private static int olderTexture;
    private static int olderFbo;
    private static int width;
    private static int height;
    private static boolean havePrevious;
    private static boolean blurHasPrevious;
    private static boolean failed;
    private static long slowSince;

    private DfrRenderer() {}

    /** Called just before Minecraft swaps the finished frame onto the screen. */
    public static void beforeShow() {
        if (failed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Dfr dfr = NexusCore.get().get(Dfr.class);
        if (!shouldRun(dfr, mc)) {
            havePrevious = false;
            return;
        }
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GlState saved = GlState.save();
        try {
            if (!ensureTextures(w, h)) {
                return;
            }
            prepare(width, height);
            copy(0, newestFbo, width, height); // the frame Minecraft just finished → "newest"
            if (!dfr.lowLag() && havePrevious && worthGenerating(mc)) {
                draw(0, 0, dfr.effectiveStrength(), 0, 0, newestTexture, olderTexture);
                GLFW.glfwSwapBuffers(GLFW.glfwGetCurrentContext()); // show the in-between frame…
                copy(newestFbo, 0, width, height);                   // …then put the real one back
                dfr.frameGenerated();
            }
            // The newest frame becomes "the one before" for next time.
            swapRoles();
            havePrevious = true;
        } catch (RuntimeException e) {
            giveUp(e);
        } finally {
            saved.restore();
        }
    }

    // ------------------------------------------------------------------ Motion Blur

    /** Motion Blur's pictures: a copy of the world, and the blurred world (two, used in turn). */
    private static int blurTarget;    // Minecraft's main picture (we don't own it)
    private static int blurTargetFbo; // our framebuffer pointing at it
    private static int worldCopy;
    private static int worldCopyFbo;
    private static final int[] trail = new int[2];
    private static final int[] trailFbo = new int[2];
    private static int blurWidth;
    private static int blurHeight;

    /**
     * Motion Blur, called when Minecraft has drawn the world but not the HUD yet (MotionBlurMixin), so
     * only the world blurs – never the hotbar, chat or text. The "trail" (the world mixed with the
     * trail so far) is kept in 16-bit colour: with normal 8-bit colour the tiny changes at high FPS
     * round away and leave ghost images that never fade.
     */
    public static void blurWorld() {
        Minecraft mc = Minecraft.getInstance();
        NexusCore core = NexusCore.get();
        Mods.MotionBlur blur = core.get(Mods.MotionBlur.class);
        if (failed || !blur.isActive() || core.get(Dfr.class).isActive() || mc.level == null) {
            blurHasPrevious = false;
            return;
        }
        int[] main = mainPicture();
        if (main[0] == 0 || main[1] <= 0 || main[2] <= 0) {
            return;
        }
        GlState saved = GlState.save();
        try {
            ensureBlurPictures(main[0], main[1], main[2]);
            prepare(blurWidth, blurHeight);
            if (!blurHasPrevious) {
                copy(blurTargetFbo, trailFbo[0], blurWidth, blurHeight); // the trail starts as this frame
                blurHasPrevious = true;
                return;
            }
            // world (copied, so we sample a texture we set up ourselves) + the trail so far → new trail
            copy(blurTargetFbo, worldCopyFbo, blurWidth, blurHeight);
            draw(trailFbo[1], 2, blur.keepThisFrame(), 0, 0, worldCopy, trail[0]);
            swap(trail);
            swap(trailFbo);
            copy(trailFbo[0], blurTargetFbo, blurWidth, blurHeight); // the blurred world goes back in
        } catch (RuntimeException e) {
            giveUp(e);
        } finally {
            saved.restore();
        }
    }

    private static void ensureBlurPictures(int target, int w, int h) {
        ensureProgram();
        if (target != blurTarget || w != blurWidth || h != blurHeight) {
            if (blurTargetFbo != 0) {
                glDeleteFramebuffers(new int[] {blurTargetFbo, worldCopyFbo, trailFbo[0], trailFbo[1]});
                glDeleteTextures(new int[] {worldCopy, trail[0], trail[1]});
            }
            blurTarget = target;
            blurWidth = w;
            blurHeight = h;
            blurTargetFbo = framebuffer(target);
            worldCopy = texture(w, h, GL_RGBA8, GL_UNSIGNED_BYTE);
            worldCopyFbo = framebuffer(worldCopy);
            for (int i = 0; i < 2; i++) {
                trail[i] = texture(w, h, GL_RGBA16F, GL_FLOAT);
                trailFbo[i] = framebuffer(trail[i]);
            }
            blurHasPrevious = false;
        }
    }

    private static void swap(int[] pair) {
        int first = pair[0];
        pair[0] = pair[1];
        pair[1] = first;
    }

    /** Called just after the swap (Low lag mode works here). */
    public static void afterShow() {
        if (failed || !havePrevious) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Dfr dfr = NexusCore.get().get(Dfr.class);
        if (!dfr.isActive() || !dfr.lowLag() || !worthGenerating(mc)) {
            return;
        }
        GlState saved = GlState.save();
        try {
            prepare(width, height);
            float[] shift = pendingCameraShift(mc);
            // After swapRoles() the frame just shown is the "older" picture.
            draw(0, 1, dfr.effectiveStrength(), shift[0], shift[1], olderTexture, olderTexture);
            GLFW.glfwSwapBuffers(GLFW.glfwGetCurrentContext());
            dfr.frameGenerated();
        } catch (RuntimeException e) {
            giveUp(e);
        } finally {
            saved.restore();
        }
    }

    // ------------------------------------------------------------------ rules

    private static boolean shouldRun(Dfr dfr, Minecraft mc) {
        if (!dfr.isActive()) {
            return false;
        }
        // Too slow for DFR: under 20 real FPS for 10 seconds → switch off and say why.
        if (mc.getFps() < 20 && mc.level != null) {
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
    private static boolean worthGenerating(Minecraft mc) {
        if (Boolean.getBoolean("nexus.dfr.force")) {
            return true; // developer self-test: always generate
        }
        int refresh = 60;
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == 0 ? null : GLFW.glfwGetVideoMode(monitor);
        if (mode != null && mode.refreshRate() > 0) {
            refresh = mode.refreshRate();
        }
        return mc.getFps() * 2 <= refresh * 1.1f;
    }

    /**
     * The camera turn that has already happened (mouse moved) but isn't in the newest frame yet, as a
     * part of the screen. Uses the same maths as Minecraft's own mouse handling.
     */
    private static float[] pendingCameraShift(Minecraft mc) {
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double factor = s * s * s * 8.0 * 0.15; // mouse movement → degrees
        double yaw = mouse.nexus$accumulatedDX() * factor;
        double pitch = mouse.nexus$accumulatedDY() * factor;
        double verticalFov = mc.options.fov().get();
        double aspect = (double) width / Math.max(1, height);
        double horizontalFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(verticalFov / 2)) * aspect));
        float x = (float) Math.max(-0.45, Math.min(0.45, yaw / horizontalFov));
        float y = (float) Math.max(-0.45, Math.min(0.45, -pitch / verticalFov));
        return new float[] {x, y};
    }

    // ------------------------------------------------------------------ OpenGL

    /** Plain drawing settings: no blending, depth, culling or clipping; all colours written. */
    private static void prepare(int w, int h) {
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_COLOR_LOGIC_OP);
        glColorMask(true, true, true, true);
        glViewport(0, 0, w, h);
    }

    /** Copies a whole picture from one framebuffer to another (0 = the window's back buffer). */
    private static void copy(int from, int to, int w, int h) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, from);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, to);
        glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    /** Runs the shader into a framebuffer (0 = the window's back buffer). */
    private static void draw(int into, int mode, float strength, float shiftX, float shiftY, int current, int previous) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, into);
        glUseProgram(program);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, current);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, previous);
        if (GlState.SAMPLERS) {
            glBindSampler(0, 0);
            glBindSampler(1, 0);
        }
        glUniform1i(uMode, mode);
        glUniform1f(uStrength, strength);
        glUniform2f(uShift, shiftX, shiftY);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private static void swapRoles() {
        int texture = newestTexture;
        int fbo = newestFbo;
        newestTexture = olderTexture;
        newestFbo = olderFbo;
        olderTexture = texture;
        olderFbo = fbo;
    }

    /** Makes the shader and the two window-sized pictures (again if the window changed size). */
    private static boolean ensureTextures(int w, int h) {
        ensureProgram();
        if (newestTexture != 0 && w == width && h == height) {
            return true;
        }
        deletePictures();
        width = w;
        height = h;
        newestTexture = texture(w, h, GL_RGBA8, GL_UNSIGNED_BYTE);
        newestFbo = framebuffer(newestTexture);
        olderTexture = texture(w, h, GL_RGBA8, GL_UNSIGNED_BYTE);
        olderFbo = framebuffer(olderTexture);
        havePrevious = false;
        return true;
    }

    /** Compiles the shader the first time it's needed. */
    private static void ensureProgram() {
        if (program == 0) {
            program = link(compile(GL_VERTEX_SHADER, VERTEX), compile(GL_FRAGMENT_SHADER, FRAGMENT));
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "Current"), 0);
            glUniform1i(glGetUniformLocation(program, "Previous"), 1);
            uMode = glGetUniformLocation(program, "Mode");
            uStrength = glGetUniformLocation(program, "Strength");
            uShift = glGetUniformLocation(program, "Shift");
            vao = glGenVertexArrays();
        }
    }

    /** A picture on the graphics card: GL_RGBA8 (normal colour) or GL_RGBA16F (precise, for the blur trail). */
    private static int texture(int w, int h, int format, int type) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, format, w, h, 0, GL_RGBA, type, (ByteBuffer) null);
        return id;
    }

    private static int framebuffer(int texture) {
        int id = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, id);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer not complete");
        }
        return id;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("shader: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static int link(int vertex, int fragment) {
        int id = glCreateProgram();
        glAttachShader(id, vertex);
        glAttachShader(id, fragment);
        glLinkProgram(id);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("program: " + glGetProgramInfoLog(id));
        }
        return id;
    }

    private static void deletePictures() {
        if (newestFbo != 0) glDeleteFramebuffers(newestFbo);
        if (olderFbo != 0) glDeleteFramebuffers(olderFbo);
        if (newestTexture != 0) glDeleteTextures(newestTexture);
        if (olderTexture != 0) glDeleteTextures(olderTexture);
        newestFbo = olderFbo = newestTexture = olderTexture = 0;
    }

    private static void giveUp(RuntimeException e) {
        failed = true;
        System.err.println("[Nexus] DFR stopped: " + e);
        NexusCore.get().noticeLater("DFR couldn't run on this graphics card and was switched off.");
    }

    /** The OpenGL settings DFR touches, saved before and put back after, exactly. */
    private static final class GlState {
        static final boolean SAMPLERS = GL.getCapabilities().OpenGL33;
        private static final int[] SWITCHES = {GL_BLEND, GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST,
                GL_COLOR_LOGIC_OP};

        private final int program = glGetInteger(GL_CURRENT_PROGRAM);
        private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        private final int readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        private final int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        private final int[] textures = new int[2];
        private final int[] samplers = new int[2];
        private final int[] viewport = new int[4];
        private final int[] colourMask = new int[4];
        private final boolean[] switches = new boolean[SWITCHES.length];

        static GlState save() {
            return new GlState();
        }

        private GlState() {
            for (int unit = 0; unit < 2; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                textures[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
                if (SAMPLERS) {
                    samplers[unit] = glGetInteger(GL_SAMPLER_BINDING);
                }
            }
            glActiveTexture(activeTexture);
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_COLOR_WRITEMASK, colourMask);
            for (int i = 0; i < SWITCHES.length; i++) {
                switches[i] = glIsEnabled(SWITCHES[i]);
            }
        }

        void restore() {
            for (int i = 0; i < SWITCHES.length; i++) {
                if (switches[i]) {
                    glEnable(SWITCHES[i]);
                } else {
                    glDisable(SWITCHES[i]);
                }
            }
            glColorMask(colourMask[0] != 0, colourMask[1] != 0, colourMask[2] != 0, colourMask[3] != 0);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            for (int unit = 0; unit < 2; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D, textures[unit]);
                if (SAMPLERS) {
                    glBindSampler(unit, samplers[unit]);
                }
            }
            glActiveTexture(activeTexture);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
            glBindVertexArray(vao);
            glUseProgram(program);
        }
    }

    /** Minecraft's main picture (the world, before the HUD is drawn on it): {OpenGL texture, width, height}. */
    private static int[] mainPicture() {
        com.mojang.blaze3d.pipeline.RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return new int[] {((com.mojang.blaze3d.opengl.GlTexture) target.getColorTexture()).glId(), target.width, target.height};
    }
}
