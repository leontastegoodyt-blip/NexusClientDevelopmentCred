package com.nexusclient.launcher.i18n;

import com.nexusclient.launcher.settings.SettingsStore;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Lang – the launcher's text in the player's language (the project brief: all launcher text lives in
 * translation files so new languages are easy to add).
 *
 * The text lives in files like lang/messages_en.properties, one line per piece of text:
 *   page.mods.title=Mods
 * To add a language, copy messages_en.properties to e.g. messages_fr.properties, translate the
 * right-hand sides, and add the language to LANGUAGES below.
 *
 * All the OTHER text on screen is translated by Translator, which looks up each English text in
 * lang/strings_<code>.json – so a new language usually only needs a strings file. LANGUAGES also picks
 * up strings files players put in <Nexus folder>/lang.
 */
public final class Lang {

    /** The languages players can pick in Settings → Language: code → name in that language. */
    public static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("en", "English");
        // Translations being tried out: <Nexus folder>/lang/strings_<code>.json (see Translator).
        java.nio.file.Path folder = com.nexusclient.launcher.game.NexusPaths.root().resolve("lang");
        try (var files = java.nio.file.Files.list(folder)) {
            files.forEach(file -> {
                String name = file.getFileName().toString();
                if (name.startsWith("strings_") && name.endsWith(".json") && !name.equals("strings_template.json")) {
                    String code = name.substring("strings_".length(), name.length() - ".json".length());
                    LANGUAGES.putIfAbsent(code, Translator.languageName(file, code));
                }
            });
        } catch (java.io.IOException e) {
            // no lang folder – only the built-in languages
        }
    }

    private static ResourceBundle bundle;

    private Lang() {}

    /** The text for a key, in the player's language (or English if it isn't translated yet). */
    public static String t(String key) {
        if (bundle == null) {
            bundle = load(SettingsStore.get().language);
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key; // shows the key itself, so missing text is easy to spot
        }
    }

    /** Like t(), but returns the fallback if there's no text for the key. */
    public static String t(String key, String fallback) {
        String text = t(key);
        return text.equals(key) ? fallback : text;
    }

    private static ResourceBundle load(String language) {
        try {
            return ResourceBundle.getBundle("com.nexusclient.launcher.lang.messages", Locale.forLanguageTag(language));
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle("com.nexusclient.launcher.lang.messages", Locale.ENGLISH);
        }
    }
}
