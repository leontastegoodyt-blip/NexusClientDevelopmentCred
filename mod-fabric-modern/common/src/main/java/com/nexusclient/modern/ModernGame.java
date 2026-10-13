package com.nexusclient.modern;

import com.mojang.blaze3d.platform.InputConstants;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.InputTracker;
import com.nexusclient.core.hud.SessionStats;
import com.nexusclient.core.module.Mods;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ModernGame – the Nexus mod's connection to Minecraft 1.20–1.21.11, the same for every mod loader.
 *
 * The loader's own start-up class (NexusModern for Fabric, NexusForge120 for Forge 1.20.1) only plugs
 * these methods into its events:
 *  - start() when the game opens, and keys() to register the Nexus keys
 *  - drawHud() every frame after Minecraft's HUD (it also draws the custom crosshair)
 *  - tick() 20 times a second, attacked() when the player hits something
 *  - joinedServer() / leftServer() and rulesMessage() for the server-safe system
 * What differs between versions lives in GameHooks (shared by versions with the same input system)
 * and VersionHooks (one per version); hooks deeper in the game are mixins.
 */
public final class ModernGame {

    /** The zoom key (read by ZoomControl) and the menu key (pressed by the self-test). */
    public static KeyMapping zoomKey;
    public static KeyMapping menuKey;
    private static KeyMapping clipKey;
    private static KeyMapping freelookKey;
    private static KeyMapping waypointKey;
    private static KeyMapping pingKey;
    private static KeyMapping emoteKey;

    private static boolean wasZooming;
    private static int lastHurtTime;
    private static Double savedTiltStrength; // the player's own hurt-cam setting, while No Hurt-Cam is on
    private static boolean sprintHeldByUs;
    private static boolean sneakHeldByUs;
    /** Players we hit in the last few seconds (entity id → when), to count kills for PvP stats. */
    private static final Map<Integer, Long> recentlyHit = new HashMap<>();
    private static boolean wasDead;

    private ModernGame() {}

    /** Starts mod-core (settings, mods, online services). Called once when the game opens. */
    public static void start(File gameDir) {
        NexusCore core = NexusCore.get();
        core.init(new FabricGameInfo(), new FabricPlatform(), gameDir);
        // Mods these versions don't do – the menu shows them as "Coming soon".
        // (DFR and, on 1.21.x, Motion Blur are drawn by ModernDfr.)
        core.markUnsupported("oldAnimations"); // 1.7 animations are a 1.8.9 thing
    }

    /** The Nexus keys (players can change them in Minecraft's Controls menu, under "Nexus Client"). */
    public static List<KeyMapping> keys() {
        if (menuKey == null) {
            menuKey = GameHooks.newKey("key.nexusclient.open_menu", InputConstants.KEY_RSHIFT);
            zoomKey = GameHooks.newKey("key.nexusclient.hold_zoom", InputConstants.KEY_C);
            clipKey = GameHooks.newKey("key.nexusclient.save_clip", InputConstants.KEY_F8);
            freelookKey = GameHooks.newKey("key.nexusclient.freelook", InputConstants.KEY_LALT);
            waypointKey = GameHooks.newKey("key.nexusclient.add_waypoint", InputConstants.KEY_B);
            pingKey = GameHooks.newKey("key.nexusclient.party_ping", InputConstants.KEY_G);
            emoteKey = GameHooks.newKey("key.nexusclient.emote", InputConstants.KEY_V);
        }
        return Arrays.asList(menuKey, zoomKey, clipKey, freelookKey, waypointKey, pingKey, emoteKey);
    }

    /** Presses a key the way Minecraft does when the player presses it (used by the self-test). */
    public static void click(KeyMapping key) {
        KeyMapping.click(InputConstants.getKey(key.saveString()));
    }

    // ------------------------------------------------------------------ HUD

    /** Draws the custom crosshair and the HUD mods (every frame, after Minecraft's own HUD). */
    public static void drawHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NexusScreenWrapper wrapper && wrapper.screen() instanceof HudEditor) {
            return; // the HUD editor draws the HUD itself (so it can be dragged around)
        }
        FabricRenderer renderer = new FabricRenderer(graphics);
        if (customCrosshair(mc)) {
            NexusCore.get().get(Mods.Crosshair.class).draw(renderer, graphics.guiWidth() / 2, graphics.guiHeight() / 2);
        }
        NexusCore.get().renderHud(renderer);
    }

    /** True if Nexus draws its own crosshair instead of Minecraft's (first person only) – see GuiMixin. */
    public static boolean customCrosshair(Minecraft mc) {
        return NexusCore.get().get(Mods.Crosshair.class).isActive() && mc.options.getCameraType().isFirstPerson();
    }

    // ------------------------------------------------------------------ events

    /** The player hit something (combo counter + PvP stats kills). */
    public static void attacked(Player player, Entity target) {
        InputTracker.hitSomething();
        if (target instanceof Player other && other != player) {
            recentlyHit.put(other.getId(), System.currentTimeMillis());
        }
    }

    /** Joined a world or server (per-server HUD layouts + that server's rules). */
    public static void joinedServer() {
        Minecraft mc = Minecraft.getInstance();
        NexusCore.get().joinedServer(mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip);
    }

    /** Left the world or server. */
    public static void leftServer() {
        Minecraft.getInstance().execute(() -> NexusCore.get().joinedServer(""));
    }

    /** The server sent its Nexus rules (channel nexusclient:rules, JSON text). */
    public static void rulesMessage(String json) {
        NexusCore.get().serverRulesMessage(json);
    }

    /** Runs 20 times a second. */
    public static void tick(Minecraft mc) {
        NexusCore core = NexusCore.get();
        if (SelfTest.enabled()) {
            SelfTest.tick(mc);
        }
        while (menuKey.consumeClick()) {
            if (mc.screen == null) {
                core.platform().openScreen(new ModMenu());
            }
        }
        while (clipKey.consumeClick()) {
            core.saveClip();
        }
        while (waypointKey.consumeClick()) {
            core.addWaypointKey();
        }
        while (pingKey.consumeClick()) {
            core.partyPingKey();
        }
        while (emoteKey.consumeClick()) {
            core.emoteKey();
        }

        // Zoom: each new press starts at the zoom level from the settings.
        Mods.Zoom zoom = core.get(Mods.Zoom.class);
        boolean zooming = zoomKey.isDown();
        if (zooming && !wasZooming) {
            zoom.target = zoom.factor.get();
        }
        wasZooming = zooming;

        freelook(mc, core);
        HitColour.tick(mc);
        BadgeText.tick(mc); // asks the Nexus server which nearby players get a badge (every 20 s)
        GlintTint.tick();   // Enchant Glint Colour

        if (mc.player == null) {
            return;
        }

        // Combo counter: being hit ends your combo.
        if (mc.player.hurtTime > lastHurtTime) {
            InputTracker.gotHit();
        }
        lastHurtTime = mc.player.hurtTime;

        pvpStats(mc);
        toggleSprintAndSneak(mc, core);
        noHurtCam(mc, core);
        core.tick();
    }

    /**
     * PvP stats: a kill is a player we hit in the last 5 seconds who then dies; a death is us
     * dying. (Other players' health is sent to every nearby client, so we can see them die.)
     */
    private static void pvpStats(Minecraft mc) {
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            SessionStats.death();
        }
        wasDead = dead;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = recentlyHit.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> hit = it.next();
            Entity entity = mc.level.getEntity(hit.getKey());
            if (entity instanceof Player target && target.isDeadOrDying()) {
                SessionStats.kill();
                it.remove();
            } else if (now - hit.getValue() > 5000 || entity == null) {
                it.remove(); // too long ago, or they left – not our kill
            }
        }
    }

    /** Toggle sprint/sneak: tap the key once, and Nexus keeps it "held down" until you tap again. */
    private static void toggleSprintAndSneak(Minecraft mc, NexusCore core) {
        Mods.ToggleSprint sprint = core.get(Mods.ToggleSprint.class);
        if (sprint.isActive()) {
            while (mc.options.keySprint.consumeClick()) {
                sprint.toggled = !sprint.toggled;
                if (!sprint.toggled) {
                    mc.options.keySprint.setDown(false);
                }
            }
            if (sprint.toggled) {
                mc.options.keySprint.setDown(true);
            }
            sprintHeldByUs = sprint.toggled;
        } else if (sprintHeldByUs) {
            mc.options.keySprint.setDown(false); // the mod was just turned off – let go of the key
            sprintHeldByUs = false;
        }

        Mods.ToggleSneak sneak = core.get(Mods.ToggleSneak.class);
        if (sneak.isActive()) {
            while (mc.options.keyShift.consumeClick()) {
                sneak.toggled = !sneak.toggled;
                if (!sneak.toggled) {
                    mc.options.keyShift.setDown(false);
                }
            }
            if (sneak.toggled) {
                mc.options.keyShift.setDown(true);
            }
            sneakHeldByUs = sneak.toggled;
        } else if (sneakHeldByUs) {
            mc.options.keyShift.setDown(false);
            sneakHeldByUs = false;
        }
    }

    /**
     * Freelook: while the key is held, switch to third person and let the mouse swing the camera
     * around (see FreelookMixin and TurnMixin). Letting go puts the old view back.
     */
    private static void freelook(Minecraft mc, NexusCore core) {
        Mods.Freelook freelook = core.get(Mods.Freelook.class);
        boolean wanted = freelook.isActive() && freelookKey.isDown() && mc.screen == null && mc.player != null;
        if (wanted && !freelook.looking) {
            freelook.start(mc.player.getYRot(), mc.player.getXRot());
            freelook.savedView = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!wanted && freelook.looking) {
            freelook.stop();
            if (freelook.savedView instanceof CameraType view) {
                mc.options.setCameraType(view);
            }
        }
    }

    /** No hurt-cam: sets Minecraft's "Damage Tilt" to 0 while on, and puts the old value back after. */
    private static void noHurtCam(Minecraft mc, NexusCore core) {
        boolean on = core.get(Mods.NoHurtCam.class).isActive();
        if (on && savedTiltStrength == null) {
            savedTiltStrength = mc.options.damageTiltStrength().get();
            mc.options.damageTiltStrength().set(0.0);
        } else if (!on && savedTiltStrength != null) {
            mc.options.damageTiltStrength().set(savedTiltStrength);
            savedTiltStrength = null;
        }
    }
}
