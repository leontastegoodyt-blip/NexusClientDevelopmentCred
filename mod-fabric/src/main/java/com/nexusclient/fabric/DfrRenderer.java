package com.nexusclient.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.dfr.Dfr;
import com.nexusclient.fabric.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * DfrRenderer – draws and shows DFR's generated frames in Minecraft 26.3 (see core/dfr/Dfr).
 *
 * Minecraft shows each finished frame by copying it to the window ("surface") and "presenting" it.
 * DfrMixin calls in here around that moment:
 *   Smooth  – before the real frame N is shown, show blend(N-1, N) first
 *   Low lag – after the real frame N is shown, show N shifted by the mouse movement already waiting
 * The in-between frame is made by a small GPU program (assets/nexusclient/shaders/core/dfr.fsh), using
 * Minecraft's own graphics layer, so it works on both its OpenGL and Vulkan versions.
 *
 * Rules (the project brief): generated frames are capped to the monitor's refresh rate (if the game is already
 * faster than half the refresh rate, extra frames would never be seen, so none are made), and DFR
 * switches itself off with a message if the PC is too slow for it (under 20 FPS for 10 seconds).
 */
public final class DfrRenderer {

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("nexusclient", "pipeline/dfr"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nexusclient", "core/dfr"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("CurrentSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("PreviousSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("ParamsSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private static GpuTexture previous;
    private static GpuTextureView previousView;
    private static GpuTexture generated;
    private static GpuTextureView generatedView;
    private static GpuTexture params;
    private static GpuTextureView paramsView;
    private static boolean havePrevious;
    private static long slowSince;
    private static boolean failed;

    private DfrRenderer() {}

    /** Called just before Minecraft copies frame `current` to the window (Smooth mode works here). */
    public static void beforeShow(GpuSurface surface, GpuTextureView current) {
        Dfr dfr = NexusCore.get().get(Dfr.class);
        if (!shouldRun(dfr)) {
            havePrevious = false;
            return;
        }
        GpuTexture texture = current.texture();
        if (!ensureTextures(texture.getWidth(0), texture.getHeight(0))) {
            return;
        }
        if (!dfr.lowLag() && havePrevious && worthGenerating()) {
            drawGenerated(current, 0, dfr.effectiveStrength(), 0, 0);
            showExtraFrame(surface, true);
            dfr.frameGenerated();
        }
        // Keep this frame, to blend with the next one.
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(texture, previous, 0, 0, 0, 0, 0, texture.getWidth(0), texture.getHeight(0));
        havePrevious = true;
    }

    private static boolean blurHasPrevious;
    /** Motion Blur's trail: the world mixed with the frames before, in 16-bit colour (two, used in turn). */
    private static final GpuTexture[] trail = new GpuTexture[2];
    private static final GpuTextureView[] trailView = new GpuTextureView[2];

    /** The same shader, writing 16-bit colour (for the Motion Blur trail). */
    private static final RenderPipeline PRECISE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("nexusclient", "pipeline/dfr_precise"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nexusclient", "core/dfr"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("CurrentSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("PreviousSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("ParamsSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_UNORM, 15))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    /**
     * Motion Blur (uses the same GPU blending as DFR), called when Minecraft has drawn the world but
     * not the HUD yet (MotionBlurMixin) – so only the world blurs, never the hotbar, chat or text.
     * The world is mixed with a "trail" of the frames before, kept in 16-bit colour: with normal 8-bit
     * colour the tiny changes at high FPS round away and leave ghost images that never fade.
     */
    public static void blurWorld() {
        Minecraft mc = Minecraft.getInstance();
        com.nexusclient.core.module.Mods.MotionBlur blur = NexusCore.get().get(com.nexusclient.core.module.Mods.MotionBlur.class);
        if (failed || !blur.isActive() || NexusCore.get().get(Dfr.class).isActive() || mc.level == null) {
            blurHasPrevious = false;
            return;
        }
        GpuTextureView world = mc.gameRenderer.mainRenderTarget().getColorTextureView();
        if (world == null || !ensureTrail(world.texture().getWidth(0), world.texture().getHeight(0))) {
            return;
        }
        if (!blurHasPrevious) {
            drawInto(trailView[0], PRECISE_PIPELINE, world, world, 0, 0, 0, 0); // the trail starts as this frame
            blurHasPrevious = true;
            return;
        }
        // world + the trail so far → the new trail (strength adjusted for the frame rate)…
        drawInto(trailView[1], PRECISE_PIPELINE, world, trailView[0], 2, blur.keepThisFrame(), 0, 0);
        GpuTexture texture = trail[0];
        trail[0] = trail[1];
        trail[1] = texture;
        GpuTextureView view = trailView[0];
        trailView[0] = trailView[1];
        trailView[1] = view;
        // …which goes back into the world picture (Smooth at strength 0 = a plain copy).
        drawInto(world, PIPELINE, trailView[0], trailView[0], 0, 0, 0, 0);
    }

    private static boolean ensureTrail(int width, int height) {
        if (trail[0] != null && trail[0].getWidth(0) == width && trail[0].getHeight(0) == height) {
            return true;
        }
        try {
            ensureParams();
            GpuDevice device = RenderSystem.getDevice();
            for (int i = 0; i < 2; i++) {
                if (trailView[i] != null) trailView[i].close();
                if (trail[i] != null) trail[i].close();
                trail[i] = device.createTexture("nexus_blur_trail", GpuTexture.USAGE_RENDER_ATTACHMENT
                        | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA16_UNORM, width, height, 1, 1);
                trailView[i] = device.createTextureView(trail[i]);
            }
            blurHasPrevious = false;
            return true;
        } catch (RuntimeException e) {
            giveUp(e);
            return false;
        }
    }

    /** Called just after Minecraft presented frame `current` (Low lag mode works here). */
    public static void afterShow(GpuSurface surface, GpuTextureView current) {
        Dfr dfr = NexusCore.get().get(Dfr.class);
        if (current == null || !shouldRun(dfr) || !dfr.lowLag() || !worthGenerating() || generated == null) {
            return;
        }
        float[] shift = pendingCameraShift();
        drawGenerated(current, 1, dfr.effectiveStrength(), shift[0], shift[1]);
        try {
            surface.acquireNextTexture();
        } catch (Exception e) {
            return; // the window is being resized/minimised – skip this one
        }
        showExtraFrame(surface, false);
        dfr.frameGenerated();
    }

    // ------------------------------------------------------------------ drawing

    private static boolean shouldRun(Dfr dfr) {
        if (failed || !dfr.isActive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
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
    private static boolean worthGenerating() {
        if (Boolean.getBoolean("nexus.dfr.force")) {
            return true; // developer self-test: always generate
        }
        Minecraft mc = Minecraft.getInstance();
        float refresh = 60;
        try {
            var monitor = mc.getWindow().findBestMonitor();
            if (monitor != null) {
                refresh = monitor.currentMode().getRefreshRate();
            }
        } catch (RuntimeException ignored) {
            // unknown monitor – assume 60 Hz
        }
        return mc.getFps() * 2 <= refresh * 1.1f;
    }

    private static boolean ensureTextures(int width, int height) {
        if (generated != null && generated.getWidth(0) == width && generated.getHeight(0) == height) {
            return true;
        }
        try {
            close();
            GpuDevice device = RenderSystem.getDevice();
            previous = device.createTexture("nexus_dfr_previous", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            previousView = device.createTextureView(previous);
            generated = device.createTexture("nexus_dfr_generated", GpuTexture.USAGE_RENDER_ATTACHMENT
                    | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            generatedView = device.createTextureView(generated);
            ensureParams();
            havePrevious = false;
            return true;
        } catch (RuntimeException e) {
            giveUp(e);
            return false;
        }
    }

    /** The tiny 2x1 picture that carries the shader's settings (made once). */
    private static void ensureParams() {
        if (params == null) {
            GpuDevice device = RenderSystem.getDevice();
            params = device.createTexture("nexus_dfr_params", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, 2, 1, 1, 1);
            paramsView = device.createTextureView(params);
        }
    }

    /** Runs the DFR shader into the "generated" picture. */
    private static void drawGenerated(GpuTextureView current, int mode, float strength, float shiftX, float shiftY) {
        drawInto(generatedView, PIPELINE, current, previousView, mode, strength, shiftX, shiftY);
    }

    /** Runs the DFR shader into a picture. */
    private static void drawInto(GpuTextureView target, RenderPipeline pipeline, GpuTextureView current,
                                 GpuTextureView previous, int mode, float strength, float shiftX, float shiftY) {
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (NativeImage image = new NativeImage(2, 1, false)) {
                // mode: 0 = Smooth (red 0), 2 = Motion Blur (red 128), 1 = Low lag (red 255)
                image.setPixel(0, 0, argb(255, mode == 0 ? 0 : mode == 2 ? 128 : 255, Math.round(strength * 255), 0));
                int x = encode16(shiftX + 0.5f);
                int y = encode16(shiftY + 0.5f);
                image.setPixel(1, 0, argb(y & 0xFF, x >> 8, x & 0xFF, y >> 8));
                encoder.writeToTexture(params, image);
            }
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            try (RenderPass pass = encoder.createRenderPass(() -> "Nexus DFR", target, Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                pass.setUniform("CurrentSampler", current, linear);
                pass.setUniform("PreviousSampler", previous, linear);
                pass.setUniform("ParamsSampler", paramsView, nearest);
                pass.draw(3, 1, 0, 0); // one triangle that covers the whole screen
            }
        } catch (RuntimeException e) {
            giveUp(e);
        }
    }

    /** Copies the generated picture to the window and shows it. */
    private static void showExtraFrame(GpuSurface surface, boolean acquireAfter) {
        if (failed || !surface.isAcquired()) {
            return;
        }
        GpuDevice device = RenderSystem.getDevice();
        surface.blitFromTexture(device.createCommandEncoder(), generatedView);
        device.createCommandEncoder().submit();
        surface.present();
        if (acquireAfter) {
            try {
                surface.acquireNextTexture(); // the real frame goes into the next window picture
            } catch (Exception e) {
                // the real frame will simply be skipped this once
            }
        }
    }

    /**
     * The camera turn that has already happened (mouse moved) but isn't in the newest frame yet, as a
     * fraction of the screen. Uses the same maths as Minecraft's own mouse handling.
     */
    private static float[] pendingCameraShift() {
        Minecraft mc = Minecraft.getInstance();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double factor = s * s * s * 8.0 * 0.15; // mouse movement → degrees
        double yaw = mouse.nexus$accumulatedDX() * factor;
        double pitch = mouse.nexus$accumulatedDY() * factor;
        double verticalFov = mc.options.fov().get();
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double horizontalFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(verticalFov / 2)) * aspect));
        float x = (float) Math.max(-0.45, Math.min(0.45, yaw / horizontalFov));
        float y = (float) Math.max(-0.45, Math.min(0.45, -pitch / verticalFov));
        return new float[] {x, y};
    }

    private static int encode16(float value) {
        return Math.max(0, Math.min(65535, Math.round(value * 65535)));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private static void giveUp(RuntimeException e) {
        failed = true;
        System.err.println("[Nexus] DFR stopped: " + e);
        NexusCore.get().noticeLater("DFR couldn't run on this graphics card and was switched off.");
        close();
    }

    private static void close() {
        for (int i = 0; i < 2; i++) {
            if (trailView[i] != null) trailView[i].close();
            if (trail[i] != null) trail[i].close();
            trail[i] = null;
            trailView[i] = null;
        }
        if (previousView != null) previousView.close();
        if (previous != null) previous.close();
        if (generatedView != null) generatedView.close();
        if (generated != null) generated.close();
        if (paramsView != null) paramsView.close();
        if (params != null) params.close();
        previous = generated = params = null;
        previousView = generatedView = paramsView = null;
    }
}
