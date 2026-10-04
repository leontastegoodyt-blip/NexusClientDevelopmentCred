package com.nexusclient.launcher.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Fonts – loads the launcher's own fonts, so the look is the same on every PC:
 *   - Inter: clean, easy-to-read text (everything by default)
 *   - Exo 2: a sporty, techy font for big titles and the logo
 * Both are free fonts (SIL Open Font License – the licence files sit next to them in
 * resources/…/fonts). After loading, theme.css can use them by name ("Inter", "Exo 2").
 *
 * The font files only include Latin letters (enough for English and most Western European
 * languages). A future language with other letters needs its matching font files added here.
 */
public final class Fonts {

    private static final String[] FILES = {
            "Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf", "Inter-Bold.ttf",
            "Exo2-Bold.ttf", "Exo2-ExtraBold.ttf"};

    private Fonts() {}

    /** Loads every font. Call once, before the window's style sheet is used. */
    public static void load() {
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/com/nexusclient/launcher/fonts/" + file)) {
                if (in == null || Font.loadFont(in, 12) == null) {
                    System.err.println("Couldn't load font " + file + " (falling back to the system font)");
                }
            } catch (IOException e) {
                System.err.println("Couldn't load font " + file + ": " + e.getMessage());
            }
        }
    }
}
