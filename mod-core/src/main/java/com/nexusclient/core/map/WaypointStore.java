package com.nexusclient.core.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WaypointStore – every waypoint the player has made, saved in config/nexus/waypoints.json.
 *
 * Waypoints belong to one world AND one dimension (a Nether waypoint isn't shown in the Overworld),
 * so they're kept in groups named "<world>|<dimension>", e.g. "mc.hypixel.net|overworld".
 */
public final class WaypointStore {

    /** One waypoint. */
    public static final class Waypoint {
        public String name;
        public int x;
        public int y;
        public int z;
        public int colour;        // 0xRRGGBB
        public boolean visible = true;

        public Waypoint(String name, int x, int y, int z, int colour) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.colour = colour;
        }
    }

    /** Colours given to new waypoints, in turn. */
    private static final int[] COLOURS = {0x3AA8FF, 0xFF6B5E, 0x3DE0A0, 0xFFB547, 0x8B5CFF, 0xFF2BD6, 0x5FD3FF, 0xFFFFFF};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, List<Waypoint>> groups = new LinkedHashMap<String, List<Waypoint>>();
    private File file;

    /** Loads the file (call once when the game starts). */
    public void load(File configFolder) {
        file = new File(configFolder, "waypoints.json");
        groups.clear();
        if (!file.exists()) {
            return;
        }
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            try {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> group : root.entrySet()) {
                    List<Waypoint> list = new ArrayList<Waypoint>();
                    for (JsonElement element : group.getValue().getAsJsonArray()) {
                        list.add(GSON.fromJson(element, Waypoint.class));
                    }
                    groups.put(group.getKey(), list);
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't read waypoints.json: " + e);
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        try {
            file.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            for (Map.Entry<String, List<Waypoint>> group : groups.entrySet()) {
                JsonArray array = new JsonArray();
                for (Waypoint waypoint : group.getValue()) {
                    array.add(GSON.toJsonTree(waypoint));
                }
                root.add(group.getKey(), array);
            }
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            try {
                GSON.toJson(root, writer);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            System.err.println("[Nexus] Couldn't save waypoints.json: " + e);
        }
    }

    /** The waypoints of one world + dimension (a live list – change it, then call save()). */
    public List<Waypoint> list(String world, String dimension) {
        String key = world + "|" + dimension;
        List<Waypoint> list = groups.get(key);
        if (list == null) {
            list = new ArrayList<Waypoint>();
            groups.put(key, list);
        }
        return list;
    }

    /** Adds a waypoint with the next colour and a name like "Waypoint 3". */
    public Waypoint add(String world, String dimension, String name, int x, int y, int z) {
        List<Waypoint> list = list(world, dimension);
        Waypoint waypoint = new Waypoint(name != null ? name : "Waypoint " + (list.size() + 1), x, y, z,
                COLOURS[list.size() % COLOURS.length]);
        list.add(waypoint);
        save();
        return waypoint;
    }
}
