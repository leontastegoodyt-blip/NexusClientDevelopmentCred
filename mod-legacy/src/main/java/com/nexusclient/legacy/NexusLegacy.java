package com.nexusclient.legacy;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.hud.InputTracker;
import com.nexusclient.core.hud.SessionStats;
import com.nexusclient.core.module.Mods;
import com.nexusclient.core.ui.HudEditor;
import com.nexusclient.core.ui.ModMenu;
import com.nexusclient.legacy.mixin.KeyBindingAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * NexusLegacy – where the Nexus mod starts inside Minecraft 1.8.9 (Legacy Fabric calls
 * onInitializeClient when the game opens). It connects the shared mod-core code to the game:
 *  - Right Shift opens the mod menu, C zooms (keys are made in LegacyKeys)
 *  - draws the HUD mods and the custom crosshair (called by InGameHudMixin)
 *  - runs the every-tick mods (toggle sprint/sneak, combo counter, PvP stats…) (MinecraftClientMixin)
 * 1.8.9 has no Fabric API here, so every hook into the game is a mixin (see the mixin package).
 */
public class NexusLegacy implements ClientModInitializer {

    private static int lastHurtTime;
    private static boolean sprintHeldByUs;
    private static boolean sneakHeldByUs;
    /** Players we hit in the last few seconds (entity id → when), to count kills for PvP stats. */
    private static final Map<Integer, Long> recentlyHit = new HashMap<Integer, Long>();
    private static boolean wasDead;
    private static boolean wasZooming;

    @Override
    public void onInitializeClient() {
        NexusCore core = NexusCore.get();
        core.init(new LegacyGameInfo(), new LegacyPlatform(), FabricLoader.getInstance().getGameDir().toFile());
        // (DFR and Motion Blur are drawn by LegacyDfr.)
    }

    /** Runs 20 times a second, at the end of Minecraft's own tick. */
    public static void tick(MinecraftClient mc) {
        NexusCore core = NexusCore.get();
        // Ask the Nexus server (every 20 s) which nearby players get a name-tag badge.
        java.util.concurrent.Callable<java.util.Collection<java.util.UUID>> nearby = () -> {
            java.util.List<java.util.UUID> ids = new java.util.ArrayList<java.util.UUID>();
            if (mc.world != null) {
                for (net.minecraft.entity.player.PlayerEntity player : mc.world.playerEntities) {
                    ids.add(player.getUuid());
                }
            }
            return ids;
        };
        com.nexusclient.core.online.Badges.tick(nearby);
        com.nexusclient.core.online.Capes.tick(nearby); // Nexus capes on nearby players
        if (SelfTest.enabled()) {
            SelfTest.tick(mc);
        }
        if (LegacyKeys.menu == null) {
            return; // the game hasn't finished starting
        }
        while (LegacyKeys.menu.wasPressed()) {
            if (mc.currentScreen == null) {
                core.platform().openScreen(new ModMenu());
            }
        }
        while (LegacyKeys.clip.wasPressed()) {
            core.saveClip();
        }
        while (LegacyKeys.waypoint.wasPressed()) {
            core.addWaypointKey();
        }
        // 1.7 Animations: clicking while blocking/eating swings the arm, like 1.7 (only on your
        // screen – this just starts the swing animation, nothing is sent to the server).
        if (mc.player != null && mc.player.isUsingItem() && mc.options.attackKey.isPressed()
                && core.get(com.nexusclient.core.module.Mods.OldAnimations.class).isActive()
                && (!mc.player.handSwinging || mc.player.handSwingTicks >= 3 || mc.player.handSwingTicks < 0)) {
            mc.player.handSwingTicks = -1;
            mc.player.handSwinging = true;
        }

        // Zoom: each new press starts at the zoom level from the settings.
        Mods.Zoom zoom = core.get(Mods.Zoom.class);
        boolean zooming = LegacyKeys.zoom.isPressed();
        if (zooming && !wasZooming) {
            zoom.target = zoom.factor.get();
        }
        wasZooming = zooming;

        freelook(mc, core);

        // Which server we're on (per-server HUD layouts + that server's rules). Checked every tick:
        // core.joinedServer does nothing if it hasn't changed.
        String server = mc.world == null || mc.getCurrentServerEntry() == null ? "" : mc.getCurrentServerEntry().address;
        core.joinedServer(server);

        if (mc.player == null || mc.world == null) {
            return;
        }

        // Combo counter: being hit ends your combo.
        if (mc.player.hurtTime > lastHurtTime) {
            InputTracker.gotHit();
        }
        lastHurtTime = mc.player.hurtTime;

        pvpStats(mc);
        toggleSprintAndSneak(mc, core);
        core.tick();
    }

    /** Draws the HUD mods and the custom crosshair (called at the end of Minecraft's HUD drawing). */
    public static void renderHud() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof NexusScreenWrapper
                && ((NexusScreenWrapper) mc.currentScreen).screen() instanceof HudEditor) {
            return; // the HUD editor draws the HUD itself (so it can be dragged around)
        }
        LegacyRenderer renderer = new LegacyRenderer();
        if (customCrosshair(mc) && !mc.options.debugEnabled) {
            Window window = new Window(mc);
            NexusCore.get().get(Mods.Crosshair.class).draw(renderer, window.getWidth() / 2, window.getHeight() / 2);
        }
        NexusCore.get().renderHud(renderer);
        com.mojang.blaze3d.platform.GlStateManager.color(1, 1, 1, 1);
    }

    /** True if Nexus draws its own crosshair instead of Minecraft's (first person only). */
    public static boolean customCrosshair(MinecraftClient mc) {
        return NexusCore.get().get(Mods.Crosshair.class).isActive() && mc.options.perspective == 0;
    }

    /** We hit something (from ClientPlayerInteractionManagerMixin). */
    public static void attacked(PlayerEntity player, Entity target) {
        InputTracker.hitSomething(); // combo counter
        if (target instanceof PlayerEntity && target != player) {
            recentlyHit.put(target.getEntityId(), System.currentTimeMillis()); // for PvP stats kills
        }
    }

    /**
     * PvP stats: a kill is a player we hit in the last 5 seconds who then dies; a death is us
     * dying. (Other players' health is sent to every nearby client, so we can see them die.)
     */
    private static void pvpStats(MinecraftClient mc) {
        boolean dead = mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
            SessionStats.death();
        }
        wasDead = dead;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = recentlyHit.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> hit = it.next();
            Entity entity = mc.world.getEntityById(hit.getKey());
            if (entity instanceof PlayerEntity && ((PlayerEntity) entity).getHealth() <= 0) {
                SessionStats.kill();
                it.remove();
            } else if (now - hit.getValue() > 5000 || entity == null) {
                it.remove(); // too long ago, or they left – not our kill
            }
        }
    }

    /** Toggle sprint/sneak: tap the key once, and Nexus keeps it "held down" until you tap again. */
    private static void toggleSprintAndSneak(MinecraftClient mc, NexusCore core) {
        Mods.ToggleSprint sprint = core.get(Mods.ToggleSprint.class);
        KeyBinding sprintKey = mc.options.sprintKey;
        if (sprint.isActive()) {
            while (sprintKey.wasPressed()) {
                sprint.toggled = !sprint.toggled;
                if (!sprint.toggled) {
                    hold(sprintKey, false);
                }
            }
            if (sprint.toggled) {
                hold(sprintKey, true);
            }
            sprintHeldByUs = sprint.toggled;
        } else if (sprintHeldByUs) {
            hold(sprintKey, false); // the mod was just turned off – let go of the key
            sprintHeldByUs = false;
        }

        Mods.ToggleSneak sneak = core.get(Mods.ToggleSneak.class);
        KeyBinding sneakKey = mc.options.sneakKey;
        if (sneak.isActive()) {
            while (sneakKey.wasPressed()) {
                sneak.toggled = !sneak.toggled;
                if (!sneak.toggled) {
                    hold(sneakKey, false);
                }
            }
            if (sneak.toggled) {
                hold(sneakKey, true);
            }
            sneakHeldByUs = sneak.toggled;
        } else if (sneakHeldByUs) {
            hold(sneakKey, false);
            sneakHeldByUs = false;
        }
    }

    /**
     * Freelook: while the key is held, switch to third person and let the mouse swing the camera
     * around (see GameRendererMixin and EntityMixin). Letting go puts the old view back.
     */
    private static void freelook(MinecraftClient mc, NexusCore core) {
        Mods.Freelook freelook = core.get(Mods.Freelook.class);
        boolean wanted = freelook.isActive() && LegacyKeys.freelook.isPressed() && mc.currentScreen == null
                && mc.player != null;
        if (wanted && !freelook.looking) {
            freelook.start(mc.player.yaw, mc.player.pitch);
            freelook.savedView = mc.options.perspective;
            mc.options.perspective = 1; // third person, behind the player
        } else if (!wanted && freelook.looking) {
            freelook.stop();
            if (freelook.savedView instanceof Integer) {
                mc.options.perspective = (Integer) freelook.savedView;
            }
        }
    }

    private static void hold(KeyBinding key, boolean down) {
        ((KeyBindingAccessor) key).nexus$setPressed(down);
    }
}
