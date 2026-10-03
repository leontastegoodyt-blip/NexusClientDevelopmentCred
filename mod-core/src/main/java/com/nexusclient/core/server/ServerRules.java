package com.nexusclient.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.module.Module;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ServerRules – the server-safe system (the project brief section 4, "Server-safe rules").
 *
 * Servers can switch off Nexus mods they don't allow, in two ways:
 *  1. The built-in list (server-rules.json inside the mod) – rules for known/partner servers.
 *  2. A message the server sends on the "nexusclient:rules" channel when you join, e.g.
 *       {"block": ["minimap", "freelook"]}        switch off these mods (by their id)
 *       {"block": ["*blockable"]}                 switch off every "server-blockable" mod
 *     Sending a new message replaces the rules from an earlier one.
 * Everything is allowed again when you leave the server. Blocked mods show "Blocked" in the menu.
 *
 * Rules can only switch mods OFF – never on – so a server can't force anything on a player.
 */
public final class ServerRules {

    /** Written in a server message or the rules file to mean "every server-blockable mod". */
    public static final String ALL_BLOCKABLE = "*blockable";

    private final Collection<Module> modules;
    private final JsonObject builtIn;
    /** Nexus Partner Servers' rules from the Nexus server (address → blocked mod ids), loaded at start. */
    private volatile java.util.Map<String, Set<String>> partners = new java.util.HashMap<String, Set<String>>();
    private final Set<String> fromList = new LinkedHashSet<String>();
    private final Set<String> fromServer = new LinkedHashSet<String>();

    public ServerRules(Collection<Module> modules) {
        this.modules = modules;
        this.builtIn = loadBuiltIn();
    }

    /**
     * The player joined a server ("" = singleplayer/menu). Loads that server's built-in rules and
     * forgets any earlier server's message. Returns the names of switched-on mods that got blocked.
     */
    public List<String> enterServer(String address) {
        fromList.clear();
        fromServer.clear();
        String host = hostOf(address);
        for (java.util.Map.Entry<String, Set<String>> partner : partners.entrySet()) {
            String domain = hostOf(partner.getKey());
            if (!host.isEmpty() && (host.equals(domain) || host.endsWith("." + domain))) {
                fromList.addAll(partner.getValue());
            }
        }
        if (!host.isEmpty() && builtIn != null) {
            for (java.util.Map.Entry<String, JsonElement> entry : builtIn.entrySet()) {
                String domain = entry.getKey().toLowerCase(Locale.ROOT);
                // "hypixel.net" also covers "mc.hypixel.net", but not "nothypixel.net".
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    fromList.addAll(readBlockList(entry.getValue()));
                }
            }
        }
        return apply();
    }

    /** The server sent a rules message (JSON text). Returns the names of switched-on mods that got blocked. */
    public List<String> receiveMessage(String json) {
        fromServer.clear();
        try {
            fromServer.addAll(readBlockList(new JsonParser().parse(json)));
        } catch (RuntimeException e) {
            System.err.println("[Nexus] Ignored a broken server rules message: " + e.getMessage());
        }
        return apply();
    }

    /**
     * Loads the Partner Servers' rules (the JSON list from GET /api/partners on the Nexus server).
     * They take effect the next time the player joins a server.
     */
    public void setPartners(String json) {
        java.util.Map<String, Set<String>> loaded = new java.util.HashMap<String, Set<String>>();
        for (JsonElement element : new JsonParser().parse(json).getAsJsonArray()) {
            JsonObject partner = element.getAsJsonObject();
            Set<String> blocked = new LinkedHashSet<String>();
            for (JsonElement id : partner.getAsJsonArray("blockedMods")) {
                blocked.add(id.getAsString());
            }
            loaded.put(partner.get("address").getAsString(), blocked);
        }
        partners = loaded;
    }

    /** Marks each module blocked or not, and lists the ones the player had on that are now blocked. */
    private List<String> apply() {
        Set<String> blocked = new LinkedHashSet<String>(fromList);
        blocked.addAll(fromServer);
        boolean allBlockable = blocked.contains(ALL_BLOCKABLE);
        List<String> newlyBlocked = new ArrayList<String>();
        for (Module module : modules) {
            boolean block = blocked.contains(module.id()) || (allBlockable && module.serverBlockable());
            if (block && !module.isBlockedByServer() && module.isEnabled()) {
                newlyBlocked.add(module.name());
            }
            module.setBlockedByServer(block);
        }
        return newlyBlocked;
    }

    /** Reads {"block": [...]} into a set of module ids. */
    private static Set<String> readBlockList(JsonElement element) {
        Set<String> ids = new LinkedHashSet<String>();
        if (element == null || !element.isJsonObject()) {
            return ids;
        }
        JsonElement block = element.getAsJsonObject().get("block");
        if (block != null && block.isJsonArray()) {
            JsonArray array = block.getAsJsonArray();
            for (JsonElement id : array) {
                ids.add(id.getAsString());
            }
        }
        return ids;
    }

    /** "Play.Example.net:25565" → "play.example.net" */
    static String hostOf(String address) {
        if (address == null) {
            return "";
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) { // not an IPv6 address
            host = host.substring(0, colon);
        }
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    /** The "servers" part of server-rules.json (bundled inside the mod). */
    private static JsonObject loadBuiltIn() {
        InputStream in = ServerRules.class.getResourceAsStream("/nexus/server-rules.json");
        if (in == null) {
            return null;
        }
        try {
            Reader reader = new InputStreamReader(in, "UTF-8");
            try {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                return root.has("servers") ? root.getAsJsonObject("servers") : null;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't read server-rules.json: " + e.getMessage());
            return null;
        }
    }
}
