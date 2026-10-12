package com.nexusclient.core.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.platform.Renderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * I18n – the in-game Nexus text (Right Shift menu, HUD editor, pop-ups, HUD labels) in the player's
 * language, the same way the launcher does it (launcher i18n/Translator).
 *
 * The launcher passes the chosen language's file as -Dnexus.langFile: a JSON file of
 * "English text": "translation". Text with numbers is looked up with the numbers replaced by "#"
 * ("3 online" → "# online") and the numbers are put back. Missing text stays English.
 *
 * It works by wrapping the Renderer (see {@link #wrap}): every piece of text drawn is translated, and
 * its width measured in the translated form, so layouts still fit. With -Dnexus.collectText=<file>
 * every English text drawn is written to that file when the game closes – a template for translators.
 */
public final class I18n {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Map<String, String> table = load();
    private static final String COLLECT = System.getProperty("nexus.collectText", System.getenv("NEXUS_COLLECT_TEXT"));
    private static final Set<String> seen = Collections.synchronizedSet(new TreeSet<String>());

    static {
        if (COLLECT != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    writeTemplate();
                }
            }, "Nexus text template"));
        }
    }

    private I18n() {}

    /** The renderer, translating every text it draws – or the same renderer if there's nothing to do. */
    public static Renderer wrap(Renderer renderer) {
        if (table.isEmpty() && COLLECT == null) {
            return renderer;
        }
        return new Translating(renderer);
    }

    /** One text in the player's language (or as it is). */
    public static String tr(String english) {
        if (english == null || english.isEmpty()) {
            return english;
        }
        if (table.isEmpty()) {
            return english;
        }
        String exact = table.get(english);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        String pattern = table.get(NUMBER.matcher(english).replaceAll("#"));
        if (pattern == null || pattern.isEmpty()) {
            return english;
        }
        Matcher numbers = NUMBER.matcher(english);
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (int hash = pattern.indexOf('#'); hash >= 0; hash = pattern.indexOf('#', from)) {
            out.append(pattern, from, hash);
            out.append(numbers.find() ? numbers.group() : "#");
            from = hash + 1;
        }
        return out.append(pattern.substring(from)).toString();
    }

    /** Remembers a drawn English text for the translators' template (not measured or shortened ones). */
    private static void collect(String english) {
        if (COLLECT != null && english != null && english.length() > 2 && !english.endsWith("…") && hasLetter(english)) {
            seen.add(NUMBER.matcher(english).replaceAll("#"));
        }
    }

    private static boolean hasLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> load() {
        String path = System.getProperty("nexus.langFile", System.getenv("NEXUS_LANG_FILE")); // (env: for developer tests)
        if (path == null || !new File(path).isFile()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<String, String>();
        try {
            Reader reader = new InputStreamReader(new FileInputStream(path), "UTF-8");
            try {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't read the translation " + path + ": " + e);
        }
        return map;
    }

    private static void writeTemplate() {
        try {
            JsonObject template = new JsonObject();
            template.addProperty("_language", "");
            synchronized (seen) {
                for (String text : seen) {
                    template.addProperty(text, "");
                }
            }
            Writer writer = new OutputStreamWriter(new FileOutputStream(COLLECT), "UTF-8");
            try {
                writer.write(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(template));
            } finally {
                writer.close();
            }
            System.out.println("[Nexus] Wrote " + seen.size() + " in-game texts to " + COLLECT);
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't write the text template: " + e);
        }
    }

    /** A Renderer that translates text on its way to the real one. */
    private static final class Translating implements Renderer {
        private final Renderer r;

        Translating(Renderer renderer) {
            this.r = renderer;
        }

        public int width() { return r.width(); }
        public int height() { return r.height(); }
        public void rect(int x1, int y1, int x2, int y2, int argb) { r.rect(x1, y1, x2, y2, argb); }
        public void outline(int x1, int y1, int x2, int y2, int argb) { r.outline(x1, y1, x2, y2, argb); }
        public void text(String text, int x, int y, int argb, boolean shadow) {
            collect(text);
            r.text(tr(text), x, y, argb, shadow);
        }
        public int textWidth(String text) { return r.textWidth(tr(text)); }
        public int lineHeight() { return r.lineHeight(); }
        public void item(Object item, int x, int y) { r.item(item, x, y); }
        public void picture(String id, int[] pixels, int w, int h, int version, int x1, int y1, int x2, int y2) {
            r.picture(id, pixels, w, h, version, x1, y1, x2, y2);
        }
        public void push(float x, float y, float scale) { r.push(x, y, scale); }
        public void pop() { r.pop(); }
        public void enableClip(int x1, int y1, int x2, int y2) { r.enableClip(x1, y1, x2, y2); }
        public void disableClip() { r.disableClip(); }
    }
}
