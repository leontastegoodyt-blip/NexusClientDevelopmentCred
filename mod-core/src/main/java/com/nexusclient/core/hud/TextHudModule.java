package com.nexusclient.core.hud;

import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;

import java.util.Collections;
import java.util.List;

/**
 * TextHudModule – the simplest kind of HUD module: one or more lines of text in a box,
 * like "FPS: 144" or the coordinates. Subclasses only say what the text is.
 */
public abstract class TextHudModule extends HudModule {

    private static final int PADDING = 4;

    protected TextHudModule(String id, String name, String description, boolean enabledByDefault,
                            double defaultX, double defaultY) {
        super(id, name, description, enabledByDefault, defaultX, defaultY);
    }

    protected TextHudModule(String id, String name, String description, com.nexusclient.core.module.Category category,
                            boolean enabledByDefault, double defaultX, double defaultY) {
        super(id, name, description, category, enabledByDefault, defaultX, defaultY);
    }

    /** The lines to show. In preview mode (HUD editor, no world), return example values. */
    protected abstract List<String> lines(GameInfo game, boolean preview);

    /** Helper for modules with a single line. */
    protected static List<String> one(String line) {
        return Collections.singletonList(line);
    }

    @Override
    protected int[] draw(Renderer renderer, GameInfo game, boolean preview) {
        List<String> lines = lines(game, preview);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, renderer.textWidth(line));
        }
        int lineHeight = renderer.lineHeight() + 1;
        int boxWidth = width + PADDING * 2;
        int boxHeight = lines.size() * lineHeight + PADDING * 2 - 1;
        drawBackground(renderer, boxWidth, boxHeight);
        for (int i = 0; i < lines.size(); i++) {
            drawText(renderer, lines.get(i), PADDING, PADDING + i * lineHeight);
        }
        return new int[] {boxWidth, boxHeight};
    }
}
