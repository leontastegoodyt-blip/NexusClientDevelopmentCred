package com.nexusclient.legacy;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import com.nexusclient.core.util.SelfTestMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

/**
 * SelfTest – a developer check for 1.8.9 (only with -Dnexus.selftest=true, see mod-legacy's build
 * file). Players never see this. Once the title screen shows it:
 *  1. opens the mod menu and HUD editor, screenshotting each
 *  2. makes a flat creative test world and joins it
 *  3. "presses" Right Shift through Minecraft's own key system and checks the mod menu opened
 *  4. screenshots the menu and the in-game HUD, then closes the game
 * Screenshots go to mod-legacy/run/screenshots.
 */
public final class SelfTest {

    private static int ticks = -1;
    private static int worldTicks = -1;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.selftest");
    }

    /** Called every tick by NexusLegacy. */
    public static void tick(MinecraftClient mc) {
        if (ticks < 0) {
            if (mc.currentScreen instanceof TitleScreen) {
                ticks = 0; // the game has finished loading – start the test
                // Don't open the pause menu if the window loses focus during the test (that would
                // block the Right Shift check, which only works with no menu open).
                mc.options.pauseOnLostFocus = false;
            }
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            log("tick " + ticks + ": screen = " + screenName(mc) + ", in world = " + (mc.world != null));
        }
        switch (ticks) {
            case 20:
                log("keys: menu = " + keyName(LegacyKeys.menu) + ", zoom = " + keyName(LegacyKeys.zoom));
                NexusCore.get().platform().openScreen(new ModMenu());
                break;
            case 60:
                screenshot(mc, "selftest-menu.png");
                break;
            case 80:
                NexusCore.get().platform().openScreen(new HudEditor());
                break;
            case 120:
                screenshot(mc, "selftest-hud-editor.png");
                break;
            case 140:
                mc.setScreen(null);
                log("making the test world");
                mc.startIntegratedServer("nexus-selftest", "Nexus self-test",
                        new LevelInfo(0L, LevelInfo.GameMode.CREATIVE, false, false, LevelGeneratorType.FLAT));
                break;
            default:
                break;
        }
        if (ticks > 140) {
            worldTest(mc);
        }
        if (ticks > 2400) {
            log("FAILED: the test world never finished loading");
            mc.scheduleStop();
        }
    }

    /** The in-world part: waits for the world, then tests the Right Shift key. */
    private static void worldTest(MinecraftClient mc) {
        if (worldTicks < 0) {
            if (mc.world != null && mc.player != null && mc.currentScreen == null) {
                worldTicks = 0;
                log("in the test world");
            }
            return;
        }
        worldTicks++;
        if (worldTicks == 40) {
            SelfTestMods.switchOn(); // midnight sky + small items, so their hooks run before the screenshot
        } else if (worldTicks == 60) {
            screenshot(mc, "selftest-hud.png");
        } else if (worldTicks == 70) {
            // The same call Minecraft makes when the player presses the key bound to the menu.
            log("pressing Right Shift (key code " + LegacyKeys.menu.getCode() + ")");
            KeyBinding.onKeyPressed(LegacyKeys.menu.getCode());
        } else if (worldTicks == 72) {
            boolean opened = mc.currentScreen instanceof NexusScreenWrapper
                    && ((NexusScreenWrapper) mc.currentScreen).screen() instanceof ModMenu;
            log(opened ? "PASSED: Right Shift opened the mod menu" : "FAILED: Right Shift didn't open the mod menu ("
                    + screenName(mc) + ")");
        } else if (worldTicks == 100) {
            screenshot(mc, "selftest-ingame-menu.png");
        } else if (worldTicks == 120) {
            log("finished");
            mc.scheduleStop();
        }
    }

    private static void screenshot(MinecraftClient mc, String name) {
        ScreenshotUtils.saveScreenshot(mc.runDirectory, name, mc.width, mc.height, mc.getFramebuffer());
        log("screenshot " + name);
    }

    private static String keyName(KeyBinding key) {
        return org.lwjgl.input.Keyboard.getKeyName(key.getCode());
    }

    private static String screenName(MinecraftClient mc) {
        if (mc.currentScreen instanceof NexusScreenWrapper) {
            return ((NexusScreenWrapper) mc.currentScreen).screen().getClass().getSimpleName();
        }
        return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
    }

    private static void log(String message) {
        System.out.println("[Nexus] self-test " + message);
    }
}
