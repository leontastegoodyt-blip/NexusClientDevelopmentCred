package com.nexusclient.core.module;

import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.settings.ChoiceSetting;
import com.nexusclient.core.settings.ColorSetting;

/**
 * NexusSettings – settings for Nexus itself (not a feature you switch on and off), shown in the mod
 * menu under Utility → "Nexus Settings".
 */
public class NexusSettings extends Module {

    /** the project brief: a different HUD setup for each server, switching automatically. */
    public final BooleanSetting perServerLayouts = add(new BooleanSetting("perServerLayouts",
            "Different HUD layout for each server", false));

    /** The accent colour of the Nexus menus. */
    public final ColorSetting accent = add(new ColorSetting("accent", "Menu accent colour", 0xFF3AA8FF));

    /** Snap HUD elements to edges and each other in the HUD editor. */
    public final BooleanSetting snapping = add(new BooleanSetting("snapping", "Snap HUD elements when moving", true));

    /** the project brief: settings follow the player between PCs (online/SettingsSync). */
    public final BooleanSetting syncSettings = add(new BooleanSetting("syncSettings", "Sync settings with my Nexus account", true));

    /** Accessibility (ui/Accessibility): colours that mean something stay easy to tell apart. */
    public final ChoiceSetting colourblind = add(new ChoiceSetting("colourblind", "Colourblind mode", "Off",
            "Off", "Red-green", "Blue-yellow"));

    /** Accessibility: menus and pop-ups appear without sliding, and long text doesn't scroll. */
    public final BooleanSetting reducedMotion = add(new BooleanSetting("reducedMotion", "Reduced motion", false));

    public NexusSettings() {
        super("nexusSettings", "Nexus Settings", "Settings for the Nexus menus and HUD.", Category.UTILITY, true, false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true); // always on – it's settings, not a feature
    }
}
