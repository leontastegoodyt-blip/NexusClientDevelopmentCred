package com.nexusclient.core;

import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.settings.Setting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ShareCodes – turns a crosshair or a HUD layout into a line of text that friends can paste
 * (the project brief: share a crosshair or HUD layout with a short code).
 *
 *   NXC-…  a crosshair (all its settings)
 *   NXH-…  a HUD layout (which HUD mods are on, where they are and how big)
 *
 * The settings are written as "key=value" lines, squeezed (gzip) and turned into safe characters
 * (base64). The Nexus servers (build step 13) can later swap these for even shorter codes.
 */
public final class ShareCodes {

    private ShareCodes() {}

    /** A code for one module's settings (used for the crosshair). */
    public static String crosshairCode(Module crosshair) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Setting<?> setting : crosshair.settings()) {
            values.put(setting.id(), setting.save());
        }
        return "NXC-" + pack(values);
    }

    /** A code for the whole HUD layout. */
    public static String hudCode() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (HudModule module : NexusCore.get().hudModules()) {
            values.put(module.id() + ".on", String.valueOf(module.isEnabled()));
            values.put(module.id() + ".x", String.valueOf(module.x()));
            values.put(module.id() + ".y", String.valueOf(module.y()));
            values.put(module.id() + ".scale", module.scale.save());
        }
        return "NXH-" + pack(values);
    }

    /**
     * Applies a pasted code. Returns a message for the player, e.g. "Crosshair imported!".
     */
    public static String apply(String code) {
        String trimmed = code.trim();
        try {
            if (trimmed.startsWith("NXC-")) {
                Module crosshair = NexusCore.get().module("crosshair");
                Map<String, String> values = unpack(trimmed.substring(4));
                for (Setting<?> setting : crosshair.settings()) {
                    if (values.containsKey(setting.id())) {
                        setting.load(values.get(setting.id()));
                    }
                }
                crosshair.setEnabled(true);
                NexusCore.get().save();
                return "Crosshair imported!";
            }
            if (trimmed.startsWith("NXH-")) {
                Map<String, String> values = unpack(trimmed.substring(4));
                for (HudModule module : NexusCore.get().hudModules()) {
                    String id = module.id();
                    if (values.containsKey(id + ".on")) {
                        module.setEnabled(Boolean.parseBoolean(values.get(id + ".on")));
                        module.setPosition(Double.parseDouble(values.get(id + ".x")),
                                Double.parseDouble(values.get(id + ".y")));
                        module.scale.load(values.get(id + ".scale"));
                    }
                }
                NexusCore.get().save();
                return "HUD layout imported!";
            }
            return "That isn't a Nexus code (they start with NX-, NXC- or NXH-).";
        } catch (Exception e) {
            return "That code is damaged or incomplete – check you copied all of it.";
        }
    }

    // ------------------------------------------------------------------ short codes (Nexus server)

    /** Gets the message for the player once a code was copied or used (on the game thread). */
    public interface Done {
        void message(String text);
    }

    /**
     * Copies a code to the clipboard. When signed in to Nexus it's a short code from the Nexus server
     * ("NX-7K3P-9QWD", kept there for friends to look up); otherwise (or if the server can't be
     * reached) the long code itself, which works without any server.
     *
     * @param kind "CROSSHAIR" or "HUD"
     */
    public static void copy(final String kind, final String longCode, final Done done) {
        if (!com.nexusclient.core.online.NexusHttp.signedIn()) {
            NexusCore.get().platform().copyToClipboard(longCode);
            done.message("Code copied!");
            return;
        }
        done.message("Making a short code…");
        com.nexusclient.core.online.NexusHttp.async(new Runnable() {
            public void run() {
                String code = longCode;
                try {
                    com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                    body.addProperty("kind", kind);
                    body.addProperty("data", longCode);
                    code = com.nexusclient.core.online.NexusHttp.post("/api/share", body).getAsJsonObject()
                            .get("code").getAsString();
                } catch (Exception e) {
                    // offline – the long code works too
                }
                final String copied = code;
                NexusCore.get().runLater(new Runnable() {
                    public void run() {
                        NexusCore.get().platform().copyToClipboard(copied);
                        done.message(copied.length() < 20 ? "Copied " + copied + "!" : "Code copied!");
                    }
                });
            }
        });
    }

    /** Uses a pasted code: a short "NX-…" one is looked up on the Nexus server first. */
    public static void use(String code, final Done done) {
        final String trimmed = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (!trimmed.matches("NX-[A-Z0-9]{4}-[A-Z0-9]{4}")) {
            done.message(apply(code));
            return;
        }
        if (!com.nexusclient.core.online.NexusHttp.signedIn()) {
            done.message("Short codes need the Nexus launcher (signed in). Ask for the long code instead.");
            return;
        }
        done.message("Looking up " + trimmed + "…");
        com.nexusclient.core.online.NexusHttp.async(new Runnable() {
            public void run() {
                String message;
                String data = null;
                try {
                    data = com.nexusclient.core.online.NexusHttp.get("/api/share/" + trimmed).getAsJsonObject()
                            .get("data").getAsString();
                    message = null;
                } catch (Exception e) {
                    message = e.getMessage();
                }
                final String found = data;
                final String error = message;
                NexusCore.get().runLater(new Runnable() {
                    public void run() {
                        done.message(found != null ? apply(found) : error);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ packing

    private static String pack(Map<String, String> values) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            OutputStream gzip = new GZIPOutputStream(bytes);
            gzip.write(text.toString().getBytes("UTF-8"));
            gzip.close();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Squeezing text in memory can't fail", e);
        }
    }

    private static Map<String, String> unpack(String code) throws IOException {
        InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(code)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = gzip.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        gzip.close();
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String line : new String(out.toByteArray(), "UTF-8").split("\n")) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                values.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return values;
    }
}
