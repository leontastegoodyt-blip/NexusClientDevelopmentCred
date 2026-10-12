package com.nexusclient.core.hud;

import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.ui.UiKit;

import java.util.Arrays;
import java.util.List;

/**
 * Custom Scoreboard – the server's sidebar scoreboard in the Nexus style: a rounded dark panel with the
 * logo gradient along the top, the title in the middle and the lines below. It can be moved and
 * resized like any HUD mod, and the red score numbers can be hidden. While it's on, Minecraft's own
 * sidebar is hidden (each version's hook asks {@link #replacesVanilla()}).
 */
public class CustomScoreboard extends HudModule {

    public final BooleanSetting hideNumbers = add(new BooleanSetting("hideNumbers", "Hide the red numbers", true));
    public final BooleanSetting accentTitle = add(new BooleanSetting("accentTitle", "Gradient line under the title", true));

    private static final List<String> SAMPLE = Arrays.asList("§e§lNEXUS SKYWARS", " \t15",
            "Players: §a8/12\t14", "Kills: §b3\t13", "  \t12", "Map: §dNeon Peaks\t11", "   \t10",
            "§bplay.nexus.gg\t9");

    public CustomScoreboard() {
        super("customScoreboard", "Scoreboard", "The server's sidebar scoreboard, Nexus style and movable.", false, 0.86, 0.46);
    }

    /** True while this replaces Minecraft's own sidebar (the hooks hide the vanilla one). */
    public boolean replacesVanilla() {
        return isActive();
    }

    @Override
    protected int[] draw(Renderer r, GameInfo game, boolean preview) {
        List<String> board = game.sidebar();
        if (preview || (board == null && game.menuOpen())) {
            board = SAMPLE; // (an example in the HUD editor, so it can be moved)
        }
        if (board == null || board.isEmpty()) {
            return new int[] {0, 0};
        }
        String title = board.get(0);
        int lines = board.size() - 1;
        // Width: the widest of the title and every "name   score" line.
        int width = r.textWidth(title) + 16;
        for (int i = 1; i <= lines; i++) {
            String[] parts = split(board.get(i));
            int w = r.textWidth(parts[0]) + (hideNumbers.isOn() ? 0 : r.textWidth(parts[1]) + 10) + 10;
            width = Math.max(width, w);
        }
        int height = 16 + lines * 10 + 4;
        if (background.isOn()) {
            UiKit.round(r, 0, 0, width, height, 5, backgroundColor.get());
        }
        UiKit.gradientLine(r, 4, 0, width - 4, 0xD0);
        UiKit.centered(r, title, width / 2, 4, textColor.get(), textShadow.isOn());
        if (accentTitle.isOn()) {
            UiKit.gradientLine(r, 8, 14, width - 8, 0x60);
        }
        int y = 18;
        for (int i = 1; i <= lines; i++) {
            String[] parts = split(board.get(i));
            r.text(parts[0], 5, y, textColor.get(), textShadow.isOn());
            if (!hideNumbers.isOn() && !parts[1].isEmpty()) {
                r.text(parts[1], width - 5 - r.textWidth(parts[1]), y, 0xFFFF6B5E, textShadow.isOn());
            }
            y += 10;
        }
        return new int[] {width, height};
    }

    private static String[] split(String line) {
        int tab = line.lastIndexOf('\t');
        return tab < 0 ? new String[] {line, ""} : new String[] {line.substring(0, tab), line.substring(tab + 1)};
    }
}
