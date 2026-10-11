package com.nexusclient.launcher.i18n;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.stage.Window;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translator – shows the WHOLE launcher in the player's language, without every piece of code having
 * to ask for translations.
 *
 * It watches the window: whenever a label, button, text box hint or tooltip gets English text, it looks
 * that text up in lang/strings_<language>.json ("English text": "translation") and shows the translation
 * instead. Text with numbers works too: "3 online" is looked up as "# online", and the numbers are put
 * back into the translation in the same order ("# en ligne" → "3 en ligne").
 *
 * Adding a language:
 *   1. copy lang/strings_template.json (every piece of launcher text, made by the screen tour with
 *      -Dnexus.collectText) to strings_<code>.json and fill in the translations
 *   2. put it in launcher resources lang/ – or, to try it out, in <Nexus folder>/lang/ – and it shows up
 *      in Settings → Language (named by its "_language" entry, e.g. "_language": "Français")
 * Missing entries simply stay in English.
 */
public final class Translator {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static Map<String, String> table = Collections.emptyMap();
    /** Text we've just set ourselves, so changing it doesn't trigger another look-up. */
    private static final Map<StringProperty, String> ours = new WeakHashMap<>();
    /** Every English text seen (only when collecting a template). */
    private static final Set<String> seen = Collections.synchronizedSet(new TreeSet<>());
    private static final boolean COLLECTING = System.getProperty("nexus.collectText") != null;
    /** Style classes of text that is never translated (add "no-translate" to any node to skip it). */
    private static final Set<String> KEEP = Set.of("no-translate", "mod-title", "mod-author", "mod-description");

    private Translator() {}

    /** Loads the language and starts watching the window. */
    public static void install(Scene scene, String language) {
        table = load(language);
        if (table.isEmpty() && !COLLECTING) {
            return; // English (or no file yet): nothing to do
        }
        watchScene(scene);
        // Right-click menus, drop-down lists and tooltips open in their own small windows.
        for (Window window : Window.getWindows()) {
            watchWindow(window);
        }
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    watchWindow(window);
                }
            }
        });
        if (COLLECTING) {
            Runtime.getRuntime().addShutdownHook(new Thread(Translator::writeTemplate, "nexus-text-template"));
        }
    }

    // ------------------------------------------------------------------ watching

    private static void watchScene(Scene scene) {
        if (scene != null && !scene.getProperties().containsKey("nexus.translated")) {
            scene.getProperties().put("nexus.translated", Boolean.TRUE);
            watch(scene.getRoot());
            scene.rootProperty().addListener((o, oldRoot, newRoot) -> watch(newRoot));
        }
    }

    private static void watchWindow(Window window) {
        if (window instanceof ContextMenu menu) {
            watchItems(menu.getItems());
        }
        watchScene(window.getScene());
        window.sceneProperty().addListener((o, oldScene, newScene) -> watchScene(newScene));
    }

    /** Menu items aren't part of a window's nodes, so they're followed separately. */
    private static void watchItems(ObservableList<MenuItem> items) {
        for (MenuItem item : items) {
            if (item.getProperties().putIfAbsent("nexus.translated", Boolean.TRUE) == null) {
                follow(item.textProperty());
                if (item instanceof Menu sub) {
                    watchItems(sub.getItems());
                }
            }
        }
    }

    private static void watch(Node node) {
        if (node == null || node.getProperties().containsKey("nexus.translated")) {
            return;
        }
        node.getProperties().put("nexus.translated", Boolean.TRUE);
        if (KEEP.stream().anyMatch(node.getStyleClass()::contains)) {
            return; // downloaded text (Modrinth names and descriptions) stays as it is
        }
        if (node instanceof Labeled labeled) {
            follow(labeled.textProperty());
        }
        if (node instanceof TextInputControl input) {
            follow(input.promptTextProperty());
        }
        if (node instanceof Control control) {
            if (control.getTooltip() != null) {
                follow(control.getTooltip().textProperty());
            }
            control.tooltipProperty().addListener((o, oldTip, newTip) -> followTooltip(newTip));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                watch(child);
            }
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    for (Node added : change.getAddedSubList()) {
                        watch(added);
                    }
                }
            });
        }
    }

    private static void followTooltip(Tooltip tooltip) {
        if (tooltip != null) {
            follow(tooltip.textProperty());
        }
    }

    /** Translates the text now and whenever it changes. */
    private static void follow(StringProperty text) {
        translate(text);
        text.addListener((o, oldValue, newValue) -> translate(text));
    }

    private static void translate(StringProperty text) {
        if (text.isBound()) {
            return; // bound text is worked out by code (often from text that's translated already)
        }
        String value = text.get();
        if (value == null || value.chars().noneMatch(Character::isLetter) || value.equals(ours.get(text))) {
            return;
        }
        if (COLLECTING) {
            seen.add(pattern(value));
        }
        String translated = lookUp(value);
        if (translated != null && !translated.equals(value)) {
            ours.put(text, translated);
            text.set(translated);
        }
    }

    // ------------------------------------------------------------------ looking up

    /** The translation of a text, or null. Exact text first, then the "# online" pattern. */
    public static String lookUp(String english) {
        String exact = table.get(english);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        String withPattern = table.get(pattern(english));
        if (withPattern == null || withPattern.isEmpty()) {
            return null;
        }
        // Put the numbers back, in order.
        Matcher numbers = NUMBER.matcher(english);
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (int hash = withPattern.indexOf('#'); hash >= 0; hash = withPattern.indexOf('#', from)) {
            out.append(withPattern, from, hash);
            out.append(numbers.find() ? numbers.group() : "#");
            from = hash + 1;
        }
        return out.append(withPattern.substring(from)).toString();
    }

    /** The text in the player's language, for the few places that aren't on screen as a label (window titles). */
    public static String tr(String english) {
        if (COLLECTING && english != null) {
            seen.add(pattern(english));
        }
        String translated = english == null ? null : lookUp(english);
        return translated != null ? translated : english;
    }

    /** "3 online" → "# online" */
    static String pattern(String text) {
        return NUMBER.matcher(text).replaceAll("#");
    }

    private static Map<String, String> load(String language) {
        if (language == null || language.equals("en")) {
            return Collections.emptyMap();
        }
        // A file in <Nexus folder>/lang wins over the built-in one, so translators can try their work
        // without rebuilding Nexus.
        Path own = com.nexusclient.launcher.game.NexusPaths.root().resolve("lang").resolve("strings_" + language + ".json");
        try (InputStream in = Files.isRegularFile(own) ? Files.newInputStream(own)
                : Translator.class.getResourceAsStream("/com/nexusclient/launcher/lang/strings_" + language + ".json")) {
            if (in == null) {
                return Collections.emptyMap();
            }
            Map<String, String> map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() { }.getType());
            return map == null ? Collections.emptyMap() : new HashMap<>(map);
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't read the " + language + " translation: " + e);
            return Collections.emptyMap();
        }
    }

    /** A translation file's language name (its "_language" entry), or the code if it has none. */
    static String languageName(Path file, String code) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> map = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
            String name = map == null ? null : map.get("_language");
            return name == null || name.isBlank() ? code : name;
        } catch (Exception e) {
            return code;
        }
    }

    /** Writes every text seen to the file given in -Dnexus.collectText (a template for translators). */
    private static void writeTemplate() {
        try {
            Map<String, String> template = new java.util.LinkedHashMap<>();
            template.put("_language", ""); // the language's own name, e.g. "Français"
            synchronized (seen) {
                for (String text : seen) {
                    template.put(text, "");
                }
            }
            Path file = Path.of(System.getProperty("nexus.collectText"));
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(template));
            System.out.println("[Nexus] Wrote " + template.size() + " texts to " + file);
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't write the text template: " + e);
        }
    }
}
