package com.nexusclient.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.hud.FrameTimes;
import com.nexusclient.core.hud.HudModule;
import com.nexusclient.core.hud.InputTracker;
import com.nexusclient.core.hud.HudMods;
import com.nexusclient.core.module.DevTools;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.module.Mods;
import com.nexusclient.core.module.NexusSettings;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Platform;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.online.ActivityReporter;
import com.nexusclient.core.record.ClipRecorder;
import com.nexusclient.core.server.ServerRules;
import com.nexusclient.core.settings.ColorSetting;
import com.nexusclient.core.settings.Setting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NexusCore – the centre of the in-game mod. It holds every module, runs them, draws the HUD, and
 * saves/loads all settings (to config/nexus/nexus.json inside the instance's game folder).
 *
 * The loader module (Fabric, Forge, 1.8.9) calls:
 *   init()        once when the game starts
 *   tick()        20 times a second
 *   renderHud()   every frame while playing
 *   joinedServer() when the player joins or leaves a server (per-server HUD layouts + server rules)
 *   serverRulesMessage() when the server sends rules on the "nexusclient:rules" channel
 */
public final class NexusCore {

    private static final NexusCore INSTANCE = new NexusCore();

    private final List<Module> modules = new ArrayList<Module>();
    private final List<HudModule> hudModules = new ArrayList<HudModule>();
    private final Map<String, Module> byId = new LinkedHashMap<String, Module>();

    private GameInfo game;
    private Platform platform;
    private File configFile;
    private JsonObject serverLayouts = new JsonObject();
    private String currentServer = "";
    private final ServerRules serverRules;
    private ClipRecorder clipRecorder;
    /** Sends a once-a-minute activity summary for clan honor (only when started by the launcher). */
    private final ActivityReporter activity = new ActivityReporter(new ActivityReporter.Notices() {
        public void show(String message) {
            noticeLater(message);
        }
    });
    private final java.util.Queue<String> pendingNotices = new java.util.concurrent.ConcurrentLinkedQueue<String>();

    private NexusCore() {
        for (HudModule module : HudMods.all()) {
            register(module);
            hudModules.add(module);
        }
        for (Module module : Mods.all()) {
            register(module);
        }
        // The developer's tools (hidden and inactive for everyone else – see online/DeveloperMode).
        for (HudModule module : DevTools.hudModules()) {
            register(module);
            hudModules.add(module);
        }
        for (Module module : DevTools.modules()) {
            register(module);
        }
        register(new NexusSettings());
        serverRules = new ServerRules(modules);
    }

    public static NexusCore get() {
        return INSTANCE;
    }

    private void register(Module module) {
        modules.add(module);
        byId.put(module.id(), module);
    }

    /**
     * Starts Nexus.
     *
     * @param gameDir the instance's game folder (settings go in config/nexus/ inside it)
     */
    public void init(GameInfo game, Platform platform, File gameDir) {
        this.game = game;
        this.platform = platform;
        this.configFile = new File(new File(gameDir, "config/nexus"), "nexus.json");
        // The launcher says where its tools folder is (FFmpeg for clips); otherwise use the game folder.
        String tools = System.getProperty("nexus.toolsDir");
        this.clipRecorder = new ClipRecorder(gameDir, tools != null ? new File(tools) : new File(gameDir, ".nexus-tools"),
                new ClipRecorder.Notices() {
                    public void show(String message) {
                        noticeLater(message);
                    }
                });
        load();
        loadPartnerRules();
        com.nexusclient.core.online.DeveloperMode.check(); // are we the Nexus developer? (asks the server)
        get(com.nexusclient.core.map.Waypoints.class).load(configFile.getParentFile());
    }

    /**
     * Nexus Partner Servers choose which Nexus mods they allow; their rules come from the Nexus server
     * (public list, no sign-in needed). Loaded in the background once when the game starts.
     */
    private void loadPartnerRules() {
        final String backend = System.getenv("NEXUS_BACKEND");
        if (backend == null) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    java.net.HttpURLConnection connection =
                            (java.net.HttpURLConnection) new java.net.URL(backend + "/api/partners").openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    java.io.InputStream in = connection.getInputStream();
                    try {
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            bytes.write(buffer, 0, read);
                        }
                        serverRules.setPartners(new String(bytes.toByteArray(), "UTF-8"));
                    } finally {
                        in.close();
                    }
                } catch (Exception e) {
                    System.err.println("[Nexus] Couldn't load Partner Server rules: " + e.getMessage());
                }
            }
        }, "Nexus partner rules");
        thread.setDaemon(true);
        thread.start();
    }

    /** Shows the player a notice on the game thread (safe to call from any thread). */
    public void noticeLater(String message) {
        pendingNotices.add(message);
    }

    /** The player pressed the "save clip" key. */
    public void saveClip() {
        Mods.ClipRecorder recorder = get(Mods.ClipRecorder.class);
        if (!recorder.isActive()) {
            noticeLater("Switch on Clip Recorder in the Nexus menu (Utility) first.");
            return;
        }
        clipRecorder.saveClip(recorder.length.intValue());
    }

    // ------------------------------------------------------------------ modules

    public List<Module> modules() {
        return Collections.unmodifiableList(modules);
    }

    public List<HudModule> hudModules() {
        return Collections.unmodifiableList(hudModules);
    }

    public Module module(String id) {
        return byId.get(id);
    }

    /** A module by its class, e.g. get(Mods.Zoom.class). */
    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        throw new IllegalArgumentException("No module " + type.getSimpleName());
    }

    public NexusSettings settings() {
        return get(NexusSettings.class);
    }

    public GameInfo game() {
        return game;
    }

    public Platform platform() {
        return platform;
    }

    /**
     * Marks features this version of the mod hasn't got yet, so the menu shows "Coming soon"
     * instead of a switch that does nothing.
     */
    public void markUnsupported(String... ids) {
        for (String id : ids) {
            Module module = byId.get(id);
            if (module != null) {
                module.setSupported(false);
            }
        }
    }

    // ------------------------------------------------------------------ running

    public void tick() {
        String notice;
        while ((notice = pendingNotices.poll()) != null) {
            platform.showNotice(notice);
        }
        if (clipRecorder != null) {
            clipRecorder.tick(get(Mods.ClipRecorder.class), game);
        }
        activity.tick(game, InputTracker.totalClicks(), game.menuOpen());
        for (Module module : modules) {
            if (module.isActive()) {
                module.onTick(game);
            }
        }
    }

    /** The "Add waypoint" key was pressed (each loader module calls this). */
    public void addWaypointKey() {
        get(com.nexusclient.core.map.Waypoints.class).addHere(game);
    }

    /** Draws every switched-on HUD mod, then the full-screen overlays. */
    public void renderHud(Renderer renderer) {
        FrameTimes.frame(); // for the frame-time graph
        for (HudModule module : hudModules) {
            if (module.isActive()) {
                module.render(renderer, game, false);
            }
        }
        // Full-screen overlays (e.g. waypoint markers), on top of the HUD boxes.
        for (Module module : modules) {
            if (module instanceof com.nexusclient.core.hud.Overlay && module.isActive()) {
                ((com.nexusclient.core.hud.Overlay) module).drawOverlay(renderer, game);
            }
        }
    }

    /**
     * The player joined a server (address) or went back to singleplayer/the menu ("").
     * With per-server layouts on, the HUD layout for the old server is saved and the new one's loaded.
     */
    public void joinedServer(String address) {
        String next = address == null ? "" : address.toLowerCase();
        if (next.equals(currentServer)) {
            return;
        }
        if (settings().perServerLayouts.isOn()) {
            serverLayouts.add(key(currentServer), layout());
            JsonElement saved = serverLayouts.get(key(next));
            if (saved != null && saved.isJsonObject()) {
                applyLayout(saved.getAsJsonObject());
            }
        }
        currentServer = next;
        save();
        tellPlayerBlocked(serverRules.enterServer(next));
    }

    /** The server sent its rules (which Nexus mods it doesn't allow) – see server/ServerRules. */
    public void serverRulesMessage(String json) {
        tellPlayerBlocked(serverRules.receiveMessage(json));
    }

    private void tellPlayerBlocked(List<String> names) {
        if (!names.isEmpty() && platform != null) {
            platform.showNotice("This server has switched off: " + join(names));
        }
    }

    private static String join(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name);
        }
        return text.toString();
    }

    private static String key(String server) {
        return server.isEmpty() ? "(singleplayer)" : server;
    }

    /** The HUD layout: which HUD mods are on, where, and how big. */
    private JsonObject layout() {
        JsonObject layout = new JsonObject();
        for (HudModule module : hudModules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("x", module.x());
            entry.addProperty("y", module.y());
            entry.addProperty("scale", module.scale.get());
            layout.add(module.id(), entry);
        }
        return layout;
    }

    private void applyLayout(JsonObject layout) {
        for (HudModule module : hudModules) {
            JsonElement element = layout.get(module.id());
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            module.setEnabled(entry.get("enabled").getAsBoolean());
            module.setPosition(entry.get("x").getAsDouble(), entry.get("y").getAsDouble());
            module.scale.set(entry.get("scale").getAsDouble());
        }
    }

    // ------------------------------------------------------------------ saving

    /** Writes every module's state and settings to nexus.json. */
    public void save() {
        if (configFile == null) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonObject moduleData = new JsonObject();
        for (Module module : modules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("favourite", module.isFavourite());
            if (module instanceof HudModule) {
                entry.addProperty("x", ((HudModule) module).x());
                entry.addProperty("y", ((HudModule) module).y());
            }
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                settings.addProperty(setting.id(), setting.save());
            }
            entry.add("settings", settings);
            moduleData.add(module.id(), entry);
        }
        root.add("modules", moduleData);
        if (settings().perServerLayouts.isOn()) {
            serverLayouts.add(key(currentServer), layout());
        }
        root.add("serverLayouts", serverLayouts);
        try {
            configFile.getParentFile().mkdirs();
            Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), "UTF-8");
            try {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("[Nexus] Couldn't save settings: " + e);
        }
    }

    private void load() {
        if (configFile == null || !configFile.exists()) {
            return;
        }
        try {
            Reader reader = new InputStreamReader(new FileInputStream(configFile), "UTF-8");
            JsonObject root;
            try {
                root = new JsonParser().parse(reader).getAsJsonObject();
            } finally {
                reader.close();
            }
            JsonObject moduleData = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module module : modules) {
                if (!moduleData.has(module.id())) {
                    continue;
                }
                JsonObject entry = moduleData.getAsJsonObject(module.id());
                if (entry.has("enabled")) module.setEnabled(entry.get("enabled").getAsBoolean());
                if (entry.has("favourite")) module.setFavourite(entry.get("favourite").getAsBoolean());
                if (module instanceof HudModule && entry.has("x")) {
                    ((HudModule) module).setPosition(entry.get("x").getAsDouble(), entry.get("y").getAsDouble());
                }
                JsonObject settings = entry.has("settings") ? entry.getAsJsonObject("settings") : new JsonObject();
                for (Setting<?> setting : module.settings()) {
                    if (settings.has(setting.id())) {
                        setting.load(settings.get(setting.id()).getAsString());
                    }
                }
                // Nexus's colours changed from cyan to the logo blue (2026-10-07). Colours still on the
                // OLD cyan default (the player never picked their own) move to the new default,
                // keeping their see-through-ness (e.g. the pressed keys in Keystrokes, the menu accent).
                for (Setting<?> setting : module.settings()) {
                    if (setting instanceof ColorSetting) {
                        ColorSetting colour = (ColorSetting) setting;
                        int newDefault = colour.defaultValue();
                        if ((colour.get() & 0xFFFFFF) == 0x00E5FF && (newDefault & 0xFFFFFF) == 0x3AA8FF) {
                            colour.set((colour.get() & 0xFF000000) | 0x3AA8FF);
                        }
                    }
                }
            }
            if (root.has("serverLayouts")) {
                serverLayouts = root.getAsJsonObject("serverLayouts");
            }
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't read settings (starting with defaults): " + e);
        }
    }
}
