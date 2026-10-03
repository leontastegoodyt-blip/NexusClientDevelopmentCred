package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Theme – applies the player's appearance and accessibility settings to the whole window, straight
 * away (no restart needed):
 *
 *   - accent colour: replaces the neon cyan everywhere (Settings → Appearance)
 *   - background picture: shown faintly behind the pages
 *   - reduced motion: switches animations off
 *   - bigger text: adds bigger-text.css
 *   - colourblind modes: adds a style file that swaps colours that are hard to tell apart
 */
public final class Theme {

    /** The see-through levels of the accent colour used in theme.css (as "-nexus-accent-080" etc.). */
    private static final List<Integer> ACCENT_ALPHAS =
            List.of(45, 60, 70, 80, 100, 120, 220, 250, 300, 350, 400, 450, 500, 550, 600);

    private static final String BASE = "/com/nexusclient/launcher/";

    private static Scene scene;
    private static Region backgroundLayer;

    private Theme() {}

    /**
     * Remembers the window's scene and the background layer, then applies the settings.
     * Called once by NexusLauncher.
     */
    public static void install(Scene windowScene, Region background) {
        scene = windowScene;
        backgroundLayer = background;
        apply();
    }

    /** Applies the current settings. Called again whenever a setting changes. */
    public static void apply() {
        if (scene == null) {
            return;
        }
        Settings settings = SettingsStore.get();
        Animations.enabled = !settings.reducedMotion;

        // --- Accent colour (colourblind "blue-yellow" mode uses pink unless the player chose one) ---
        String accent = settings.accentColor;
        if (accent == null && "BLUE_YELLOW".equals(settings.colourblindMode)) {
            accent = "#FF4FA3";
        }
        StringBuilder style = new StringBuilder();
        if (accent != null) {
            Color color = Color.web(accent);
            style.append("-nexus-cyan: ").append(accent).append(';');
            for (int alpha : ACCENT_ALPHAS) {
                style.append(String.format(Locale.ROOT, "-nexus-accent-%03d: rgba(%d, %d, %d, %.3f);", alpha,
                        (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255),
                        (int) Math.round(color.getBlue() * 255), alpha / 1000.0));
            }
        }
        // Inline styles on the top of the window beat the theme file, so this overrides the colours.
        scene.getRoot().setStyle(style.toString());

        // --- Extra style files: bigger text, colourblind colours ---
        List<String> sheets = scene.getStylesheets();
        sheets.removeIf(sheet -> !sheet.endsWith("/theme.css"));
        if (settings.biggerText) {
            sheets.add(resource("bigger-text.css"));
        }
        if ("RED_GREEN".equals(settings.colourblindMode)) {
            sheets.add(resource("colourblind-red-green.css"));
        } else if ("BLUE_YELLOW".equals(settings.colourblindMode)) {
            sheets.add(resource("colourblind-blue-yellow.css"));
        }

        // --- Background picture ---
        if (backgroundLayer != null) {
            Path picture = settings.backgroundImage == null ? null : Path.of(settings.backgroundImage);
            if (picture != null && Files.exists(picture)) {
                backgroundLayer.setStyle("-fx-background-image: url(\"" + picture.toUri() + "\");"
                        + "-fx-background-size: cover; -fx-background-position: center;");
                backgroundLayer.setVisible(true);
            } else {
                backgroundLayer.setStyle("");
                backgroundLayer.setVisible(false);
            }
        }
    }

    private static String resource(String name) {
        return Theme.class.getResource(BASE + name).toExternalForm();
    }
}
