package com.nexusclient.modern;

import com.mojang.blaze3d.platform.InputConstants;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.InputTracker;
import com.nexusclient.core.hud.SessionStats;
import com.nexusclient.core.module.Mods;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * NexusModern – where the Nexus mod starts inside Minecraft 1.20–1.21.11 (Fabric calls
 * onInitializeClient when the game opens). It connects the shared mod-core code to the game:
 *  - Right Shift opens the mod menu, C zooms, F8 saves a clip
 *  - draws the HUD mods and the custom crosshair
 *  - runs the every-tick mods (toggle sprint/sneak, no hurt-cam, combo counter…)
 *
 * This file is the same for every 1.20–1.21 version. What differs between versions lives in
 * GameHooks (shared by the versions with the same input system) and VersionHooks (one per version).
 */
public class NexusModern implements ClientModInitializer {

    /** The zoom key (read by ZoomControl). */
    public static KeyMapping zoomKey;
    public static KeyMapping menuKey;
    private static KeyMapping clipKey;
    private static KeyMapping freelookKey;

    private boolean wasZooming;
    private int lastHurtTime;
    private Double savedTiltStrength; // the player's own hurt-cam setting, while No Hurt-Cam is on
    private boolean sprintHeldByUs;
    private boolean sneakHeldByUs;
    /** Players we hit in the last few seconds (entity id → when), to count kills for PvP stats. */
    private final Map<Integer, Long> recentlyHit = new HashMap<>();
    private boolean wasDead;

    @Override
    public void onInitializeClient() {
        Minecraft mc = Minecraft.getInstance();
        NexusCore core = NexusCore.get();
        core.init(new FabricGameInfo(), new FabricPlatform(), FabricLoader.getInstance().getGameDir().toFile());

        // Mods these versions don't do yet – the menu shows them as "Coming soon".
        core.markUnsupported("oldAnimations", "itemPhysics", "enchantGlint", "motionBlur", "waypoints", "minimap",
                "screenshotUploader", "dfr");

        // Keys (players can change them in Minecraft's Controls menu, under "Nexus Client").
        menuKey = GameHooks.registerKey("key.nexusclient.open_menu", InputConstants.KEY_RSHIFT);
        zoomKey = GameHooks.registerKey("key.nexusclient.hold_zoom", InputConstants.KEY_C);
        clipKey = GameHooks.registerKey("key.nexusclient.save_clip", InputConstants.KEY_F8);
        freelookKey = GameHooks.registerKey("key.nexusclient.freelook", InputConstants.KEY_LALT);

        // HUD: draw the custom crosshair and every switched-on HUD mod on top of the normal game HUD.
        GameHooks.registerHud(graphics -> renderHud(mc, graphics));

        // Combo counter: count our hits on other players/mobs.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                InputTracker.hitSomething();
                if (entity instanceof Player target && target != player) {
                    recentlyHit.put(target.getId(), System.currentTimeMillis()); // for PvP stats kills
                }
            }
            return InteractionResult.PASS;
        });

        // Server-safe system: tell Nexus which server we're on (per-server HUD layouts + that
        // server's rules), and listen for the server's own rules message.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                core.joinedServer(client.getCurrentServer() == null ? "" : client.getCurrentServer().ip));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> core.joinedServer("")));
        VersionHooks.registerRulesChannel(core::serverRulesMessage);

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, core));
    }

    /** Draws the custom crosshair and the HUD mods (every frame, after Minecraft's own HUD). */
    private static void renderHud(Minecraft mc, GuiGraphics graphics) {
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

    /** Runs 20 times a second. */
    private void tick(Minecraft mc, NexusCore core) {
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

        // Zoom: each new press starts at the zoom level from the settings.
        Mods.Zoom zoom = core.get(Mods.Zoom.class);
        boolean zooming = zoomKey.isDown();
        if (zooming && !wasZooming) {
            zoom.target = zoom.factor.get();
        }
        wasZooming = zooming;

        freelook(mc, core);
        HitColour.tick(mc);

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
    private void pvpStats(Minecraft mc) {
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
    private void toggleSprintAndSneak(Minecraft mc, NexusCore core) {
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
    private void freelook(Minecraft mc, NexusCore core) {
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
    private void noHurtCam(Minecraft mc, NexusCore core) {
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
