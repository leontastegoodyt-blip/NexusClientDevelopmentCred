package com.nexusclient.fabric;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * SelfTest – a developer check (only when started with -Dnexus.selftest=true, see mod-fabric's
 * build file). Once the title screen shows, it tries DFR, opens the mod menu and HUD editor by
 * itself (screenshotting each), then makes a test world, presses Right Shift through Minecraft's own
 * key system, checks the mod menu opened, and closes the game. Players never see this.
 */
public final class SelfTest {

    private static int ticks = -1;
    private static int worldTicks = -1;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.selftest");
    }

    /** Called every tick by NexusFabric. */
    public static void tick(Minecraft mc) {
        if (ticks < 0) {
            if (mc.gui.screen() instanceof TitleScreen) {
                ticks = 0; // the game has finished loading – start the test
            }
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            Object screen = mc.gui.screen();
            System.out.println("[Nexus] self-test tick " + ticks + ": screen = "
                    + (screen instanceof NexusScreenWrapper w ? w.screen().getClass().getSimpleName()
                    : screen == null ? "none" : screen.getClass().getSimpleName()));
        }
        com.nexusclient.core.dfr.Dfr dfr = NexusCore.get().get(com.nexusclient.core.dfr.Dfr.class);
        if (ticks == 10) {
            dfr.mode.set("Creative"); // Smooth blending
            dfr.setEnabled(true);
        }
        if (ticks == 60 || ticks == 130) {
            System.out.println("[Nexus] self-test DFR (" + (dfr.lowLag() ? "Low lag" : "Smooth") + "): real FPS "
                    + mc.getFps() + ", generated FPS " + dfr.generatedFps());
        }
        if (ticks == 70) {
            dfr.mode.set("PvP");
            dfr.pvpStyle.set("Low lag");
        }
        if (ticks == 140) {
            dfr.setEnabled(false);
        }
        switch (ticks) {
            case 100 -> {
                // Check the keys are really bound to Right Shift and C (Minecraft's own key names).
                System.out.println("[Nexus] self-test keys: menu = " + NexusFabric.menuKey.getTranslatedKeyMessage().getString()
                        + ", zoom = " + NexusFabric.zoomKey.getTranslatedKeyMessage().getString());
                NexusCore.get().platform().openScreen(new ModMenu());
            }
            case 160 -> Screenshot.grab(mc, false);
            case 180 -> NexusCore.get().platform().openScreen(new HudEditor());
            case 240 -> Screenshot.grab(mc, false);
            case 260 -> {
                mc.options.pauseOnLostFocus = false; // a pause menu would block the Right Shift check
                mc.setScreenAndShow(null);
                System.out.println("[Nexus] self-test making the test world");
                mc.createWorldOpenFlows().createFreshLevel("Nexus self-test " + System.currentTimeMillis(),
                        MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions,
                        new TitleScreen());
            }
            default -> { }
        }
        if (ticks > 260) {
            worldTest(mc);
        }
        if (ticks > 3600) {
            System.out.println("[Nexus] self-test FAILED: the test world never finished loading");
            mc.stop();
        }
    }

    /** The in-world part: waits for the world, then presses Right Shift like a player would. */
    private static void worldTest(Minecraft mc) {
        if (worldTicks < 0) {
            if (mc.level != null && mc.player != null && mc.gui.screen() == null) {
                worldTicks = 0;
                System.out.println("[Nexus] self-test in the test world");
            }
            return;
        }
        worldTicks++;
        if (worldTicks == 70) {
            // The same call Minecraft makes when the player presses the key bound to the menu.
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(NexusFabric.menuKey));
        } else if (worldTicks == 72) {
            boolean opened = mc.gui.screen() instanceof NexusScreenWrapper w && w.screen() instanceof ModMenu;
            System.out.println(opened ? "[Nexus] self-test PASSED: Right Shift opened the mod menu"
                    : "[Nexus] self-test FAILED: Right Shift didn't open the mod menu (" + mc.gui.screen() + ")");
        } else if (worldTicks == 100) {
            Screenshot.grab(mc, false);
        } else if (worldTicks == 120) {
            System.out.println("[Nexus] self-test finished");
            mc.stop();
        }
    }
}
