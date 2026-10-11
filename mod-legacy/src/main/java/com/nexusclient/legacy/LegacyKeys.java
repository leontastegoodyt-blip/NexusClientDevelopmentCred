package com.nexusclient.legacy;

import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * LegacyKeys – Nexus's keys in 1.8.9: Right Shift = mod menu, C = zoom, F8 = save clip,
 * Left Alt = freelook.
 * Players can change them in Options → Controls, under "Nexus Client".
 *
 * They're made by GameOptionsMixin while Minecraft builds its key list, so they're saved in
 * options.txt like the game's own keys. (1.8.9 can't load mod translation files without Legacy
 * Fabric API, so the names are plain English instead of translation keys.)
 */
public final class LegacyKeys {

    public static final String CATEGORY = "Nexus Client";

    public static KeyBinding menu;
    public static KeyBinding zoom;
    public static KeyBinding clip;
    public static KeyBinding freelook;
    public static KeyBinding waypoint;
    public static KeyBinding ping;

    private LegacyKeys() {}

    /** Makes the keys (once) and returns them, to be added to Minecraft's key list. */
    public static KeyBinding[] create() {
        if (menu == null) {
            menu = new KeyBinding("Open Nexus menu", Keyboard.KEY_RSHIFT, CATEGORY);
            zoom = new KeyBinding("Zoom (hold)", Keyboard.KEY_C, CATEGORY);
            clip = new KeyBinding("Save clip", Keyboard.KEY_F8, CATEGORY);
            freelook = new KeyBinding("Freelook (hold)", Keyboard.KEY_LMENU, CATEGORY); // Left Alt
            waypoint = new KeyBinding("Add waypoint here", Keyboard.KEY_B, CATEGORY);
            ping = new KeyBinding("Party ping", Keyboard.KEY_G, CATEGORY);
        }
        return new KeyBinding[] {menu, zoom, clip, freelook, waypoint, ping};
    }
}
