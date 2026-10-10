package com.nexusclient.launcher.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Fonts – loads the launcher's own title font, Exo 2: a sporty, techy font for big titles and
 * the logo. It's free (SIL Open Font License – the licence file sits next to it in
 * resources/…/fonts). After loading, theme.css can use it by name ("Exo 2").
 *
 * Body text deliberately uses the system font instead: it has every letter (other alphabets,
 * fancy symbols in server messages), while Exo 2 only has Latin letters.
 */
public final class Fonts {

    private static final String[] FILES = {"Exo2-Bold.ttf", "Exo2-ExtraBold.ttf"};

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
