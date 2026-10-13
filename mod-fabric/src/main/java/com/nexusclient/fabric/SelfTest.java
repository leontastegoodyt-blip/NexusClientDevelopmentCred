package com.nexusclient.fabric;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import com.nexusclient.core.util.SelfTestMods;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
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
    private static int waitTicks;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("nexus.selftest");
    }

    /** Called every tick by NexusFabric. */
    public static void tick(Minecraft mc) {
        if (ticks < 0) {
            if (mc.gui.screen() instanceof TitleScreen) {
                ticks = 0; // the game has finished loading – start the test
            } else if (mc.gui.screen() instanceof AccessibilityOnboardingScreen) {
                // Minecraft's very-first-start screen doesn't always move on by itself – skip it.
                mc.setScreenAndShow(new TitleScreen());
            } else if (++waitTicks % 100 == 0) {
                System.out.println("[Nexus] self-test waiting for the title screen: screen = " + mc.gui.screen());
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
        if (worldTicks == 40) {
            SelfTestMods.switchOn(); // midnight sky + small items, so their hooks run before the screenshot
            // An enchanted sword in hand (glint colour) and some diamonds on the ground (item physics).
            // Both ways of writing the item are sent: older versions use "Count:3b", newer "count:3".
            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    for (String command : new String[] {"give @p minecraft:diamond_sword", "enchant @p minecraft:sharpness 1",
                            "execute at @p rotated as @p run summon item ^ ^1 ^3 {Item:{id:\"minecraft:diamond\",count:3}}",
                            "execute at @p rotated as @p run summon item ^ ^1 ^3 {Item:{id:\"minecraft:diamond\",Count:3b}}",
                            // A sidebar scoreboard and a boss bar, for Custom Scoreboard / Custom Boss Bar.
                            "scoreboard objectives add nexus dummy \"NEXUS TEST\"", "scoreboard objectives setdisplay sidebar nexus",
                            "scoreboard players set Kills nexus 3", "scoreboard players set Coins nexus 250",
                            "bossbar add nexus:test \"Event ends soon\"", "bossbar set nexus:test players @a",
                            "bossbar set nexus:test value 60", "bossbar set nexus:test color purple"}) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                    }
                });
            }
            // Face the player (F5) with the developer badge forced on, so the screenshot shows the
            // name tag with its Nexus badge and DEV tag.
            System.setProperty("nexus.badgeTest", "DEVELOPER");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); // from behind: cape + name tag
        } else if (worldTicks == 50) {
            com.nexusclient.core.NexusCore.get().emoteKey(); // Wave (Emotes), shown from the front at noon
            com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.module.Mods.TimeChanger.class).time.set(6000.0);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (worldTicks == 56) {
            Screenshot.grab(mc, false); // the wave, from the front
            com.nexusclient.core.NexusCore.get().get(com.nexusclient.core.module.Mods.TimeChanger.class).time.set(18000.0);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else if (worldTicks == 66) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else if (worldTicks == 69) {
            Screenshot.grab(mc, false); // first person: minimap + waypoint marker
        } else if (worldTicks == 60) {
            Screenshot.grab(mc, false);
        } else if (worldTicks == 70) {
            // The same call Minecraft makes when the player presses the key bound to the menu.
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(NexusFabric.menuKey));
        } else if (worldTicks == 72) {
            boolean opened = mc.gui.screen() instanceof NexusScreenWrapper w && w.screen() instanceof ModMenu;
            System.out.println(opened ? "[Nexus] self-test PASSED: Right Shift opened the mod menu"
                    : "[Nexus] self-test FAILED: Right Shift didn't open the mod menu (" + mc.gui.screen() + ")");
        } else if (worldTicks == 100) {
            Screenshot.grab(mc, false);
        } else if (worldTicks == 104) {
            // A mod's settings page, for the screenshot below.
            NexusCore.get().platform().openScreen(com.nexusclient.core.ui.ModMenu.forModule(NexusCore.get().module("crosshair")));
        } else if (worldTicks == 114) {
            Screenshot.grab(mc, false);
        } else if (worldTicks == 118) {
            NexusCore.get().platform().closeScreen();
        } else if (worldTicks > 120 && worldTicks < 136 && mc.player != null) {
            // Motion Blur (switched on by SelfTestMods): spin the camera so the screenshot shows a trail.
            mc.player.setYRot(mc.player.getYRot() + 12);
            if (worldTicks == 135) {
                Screenshot.grab(mc, false);
            }
        } else if (worldTicks >= 140 && (!com.nexusclient.core.util.SocialSelfTest.wanted()
                || com.nexusclient.core.util.SocialSelfTest.step(worldTicks - 140))) {
            System.out.println("[Nexus] self-test finished");
            mc.stop();
        }
    }
}
