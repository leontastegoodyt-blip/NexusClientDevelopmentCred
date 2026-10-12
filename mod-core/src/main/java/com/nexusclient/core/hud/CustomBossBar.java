package com.nexusclient.core.hud;

import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.ui.UiKit;

import java.util.Arrays;
import java.util.List;

/**
 * Custom Boss Bar – boss bars (the Ender Dragon, the Wither, and the bars servers use for timers and
 * events) as slim rounded bars that can be moved and resized. Colours: the boss's own colour, or the
 * Nexus gradient. While it's on, Minecraft's own boss bars are hidden (see {@link #replacesVanilla()}).
 */
public class CustomBossBar extends HudModule {

    public final BooleanSetting showName = add(new BooleanSetting("showName", "Show the name", true));
    public final BooleanSetting nexusColours = add(new BooleanSetting("nexusColours", "Nexus gradient instead of the boss colour", false));
    public final BooleanSetting showPercent = add(new BooleanSetting("showPercent", "Show percent", true));

    private static final int WIDTH = 182;
    private static final List<String> SAMPLE = Arrays.asList("§dEnder Dragon\t0.72\tPINK",
            "§eEvent ends in §f4:32\t0.35\tYELLOW");

    public CustomBossBar() {
        super("customBossBar", "Boss Bar", "Boss bars as slim bars you can move and resize.", false, 0.36, 0.01);
    }

    public boolean replacesVanilla() {
        return isActive();
    }

    @Override
    protected int[] draw(Renderer r, GameInfo game, boolean preview) {
        List<String> bars = game.bossBars();
        if (preview || (bars.isEmpty() && game.menuOpen())) {
            bars = SAMPLE;
        }
        if (bars.isEmpty()) {
            return new int[] {0, 0};
        }
        int y = 0;
        for (String bar : bars) {
            String[] parts = bar.split("\t", -1);
            String name = parts[0];
            double progress = parts.length > 1 ? parse(parts[1]) : 1;
            int colour = parts.length > 2 ? colour(parts[2]) : 0xFFB98CFF;
            if (showName.isOn()) {
                String label = showPercent.isOn() ? name + "  §7" + Math.round(progress * 100) + "%" : name;
                UiKit.centered(r, label, WIDTH / 2, y, textColor.get(), textShadow.isOn());
                y += 10;
            }
            // The bar: a dark track with a soft edge, filled from the left.
            UiKit.round(r, 0, y, WIDTH, y + 6, 3, background.isOn() ? backgroundColor.get() : 0x60000000);
            int filled = (int) Math.round(WIDTH * Math.max(0, Math.min(1, progress)));
            if (filled > 0) {
                if (nexusColours.isOn()) {
                    for (int x = 0; x < filled; x += 2) {
                        r.rect(x, y + 1, Math.min(filled, x + 2), y + 5, 0xFF000000 | UiKit.gradient(x / (double) WIDTH));
                    }
                } else {
                    UiKit.round(r, 0, y + 1, Math.max(2, filled), y + 5, 2, colour);
                }
                r.rect(0, y + 1, filled, y + 2, 0x40FFFFFF); // a thin shine along the top
            }
            y += 10;
        }
        return new int[] {WIDTH, y};
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Minecraft's boss bar colours, a little brighter to suit the dark style. */
    private static int colour(String name) {
        if (name.equals("PINK")) return 0xFFFF6FD0;
        if (name.equals("BLUE")) return 0xFF3AA8FF;
        if (name.equals("RED")) return 0xFFFF5A5A;
        if (name.equals("GREEN")) return 0xFF3DE0A0;
        if (name.equals("YELLOW")) return 0xFFFFD84A;
        if (name.equals("PURPLE")) return 0xFFB98CFF;
        return 0xFFE8E8F0; // WHITE
    }
}
