package com.nexusclient.core.platform;

import java.util.List;

/**
 * GameInfo – what mod-core can read about the game. Each loader module implements it with that
 * version of Minecraft (e.g. fps() asks Minecraft for its FPS counter).
 *
 * Everything must be safe to call when not in a world (then it returns zeros/empty lists), because the
 * HUD editor shows previews on the title screen too.
 */
public interface GameInfo {

    /** One item stack: the platform's own item object (for drawing), its count and durability. */
    final class ItemInfo {
        public final Object handle;
        public final String name;
        public final int count;
        public final int damage;
        public final int maxDamage;

        public ItemInfo(Object handle, String name, int count, int damage, int maxDamage) {
            this.handle = handle;
            this.name = name;
            this.count = count;
            this.damage = damage;
            this.maxDamage = maxDamage;
        }

        /** Durability left, e.g. 250 of 250 → 250. 0 max = the item doesn't wear out. */
        public int durabilityLeft() {
            return maxDamage - damage;
        }
    }

    /** One active potion effect. */
    final class EffectInfo {
        public final String name;
        public final int amplifier;    // 0 = level I, 1 = level II…
        public final int durationTicks; // 20 ticks = 1 second; -1 = infinite

        public EffectInfo(String name, int amplifier, int durationTicks) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
        }
    }

    boolean inWorld();

    int fps();

    /** Ping to the server in milliseconds (0 in singleplayer). */
    int ping();

    double x();

    double y();

    double z();

    /** Which way the player looks, in degrees (Minecraft's yaw). */
    float yaw();

    /** e.g. "Plains" */
    String biome();

    /** e.g. "mc.hypixel.net", or "Singleplayer" */
    String serverAddress();

    /** The in-game time of day, 0–24000 (0 = sunrise). */
    long dayTime();

    /** Whether the game's key for this action is held: "forward", "back", "left", "right", "jump", "sneak", "attack", "use". */
    boolean isKeyDown(String action);

    /** Armour from helmet to boots (empty slots are left out). */
    List<ItemInfo> armor();

    /** The items in the main hand and off hand (empty hands are left out). */
    List<ItemInfo> heldItems();

    /** How many of an item the player has in total, e.g. "minecraft:arrow". */
    int countItem(String itemId);

    /** An icon for an item type, for the item counter (null if unknown). */
    Object itemIcon(String itemId);

    List<EffectInfo> effects();

    /** Names of the resource packs switched on (highest first). */
    List<String> resourcePacks();

    /** The player's own name. */
    String playerName();

    /** The game window's position and size on screen, in pixels: {x, y, width, height}. */
    int[] windowBounds();

    /** True if the game fills the whole screen (fullscreen). */
    boolean fullscreen();

    /** True while a menu or other screen is open (inventory, chat, pause menu…). */
    boolean menuOpen();

    // ------------------------------------------------------------------ for waypoints and the minimap
    // (These have safe defaults, so a version that doesn't answer them yet still works.)

    /** How far up/down the player looks, in degrees: -90 = straight up, 90 = straight down. */
    default float pitch() {
        return 0;
    }

    /** The height of the player's eyes (where the camera is in first person). */
    default double eyeY() {
        return y() + 1.62;
    }

    /** The camera's field of view in degrees (top to bottom of the screen). */
    default float fov() {
        return 70;
    }

    /** Which world this is, for saving waypoints: the server address, or "world:<name>" in singleplayer. */
    default String worldKey() {
        return serverAddress();
    }

    /** "overworld", "the_nether" or "the_end" (other names for modded dimensions). */
    default String dimension() {
        return "overworld";
    }

    /** The player's health (20 = 10 full hearts, 0 = dead). */
    default float health() {
        return 20;
    }

    /**
     * The map colour of the highest block at (x, z), as 0xRRGGBB – the colours Minecraft uses on
     * paper maps. 0 = nothing there or not loaded yet.
     */
    default int mapColour(int x, int z) {
        return 0;
    }

    /** The height of the highest block at (x, z) (for shading hills on the minimap). */
    default int surfaceY(int x, int z) {
        return 0;
    }

    /**
     * The sidebar scoreboard (Custom Scoreboard): the first entry is the title, then one "name	score"
     * line per row, top first. Text may use '00a7' colour codes. null = there's no sidebar.
     */
    default List<String> sidebar() {
        return null;
    }

    /** Boss bars (Custom Boss Bar): "name	progress 0-1	colour" each (colour: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE). */
    default List<String> bossBars() {
        return java.util.Collections.emptyList();
    }

    /** The block the crosshair points at ({x, y, z}), or null if none (used by party pings). */
    default int[] lookedAtBlock() {
        return null;
    }
}
