package com.nexusclient.launcher.errorscan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexusclient.launcher.game.NexusPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ScanHistory – a record of past scans and fixes (the project brief: keeps a history of past scans), saved in
 * error-history.json. Each fix remembers its "undo steps", so it can be undone later.
 */
public final class ScanHistory {

    /**
     * One small change a fix made, and how to reverse it.
     *   MOVE a → b         (undo: move b back to a)
     *   ADDED a            (undo: delete a – a file the fix downloaded)
     *   SET_RAM id, old    (undo: set the instance's memory back to old)
     *   SHADERS id, old    (undo: set shaders back to on/off)
     */
    public record Step(String action, String a, String b) {}

    /**
     * One scan (and the fix, if one was made).
     *
     * @param kind "CRASH", "PRE_LAUNCH" or "MANUAL"
     */
    public record Entry(String id, long time, String instanceId, String instanceName, String kind,
                        List<String> problems, String fixDescription, List<Step> undo, boolean undone) {

        public boolean canUndo() {
            return fixDescription != null && undo != null && !undo.isEmpty() && !undone;
        }
    }

    private static final int MAX_ENTRIES = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = NexusPaths.root().resolve("error-history.json");

    private ScanHistory() {}

    public static synchronized List<Entry> list() {
        try {
            if (Files.exists(FILE)) {
                List<Entry> entries = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Entry>>() {}.getType());
                if (entries != null) {
                    return new ArrayList<>(entries);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /** Adds a scan to the top of the history. */
    public static synchronized Entry add(String instanceId, String instanceName, String kind, List<Diagnosis> problems,
                                         String fixDescription, List<Step> undo) {
        List<Entry> entries = list();
        Entry entry = new Entry(UUID.randomUUID().toString(), System.currentTimeMillis(), instanceId, instanceName, kind,
                problems.stream().map(Diagnosis::title).toList(), fixDescription, undo, false);
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        save(entries);
        return entry;
    }

    /** Marks an entry's fix as undone. */
    public static synchronized void markUndone(String id) {
        List<Entry> entries = list();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.id().equals(id)) {
                entries.set(i, new Entry(e.id(), e.time(), e.instanceId(), e.instanceName(), e.kind(), e.problems(),
                        e.fixDescription(), e.undo(), true));
            }
        }
        save(entries);
    }

    private static void save(List<Entry> entries) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(entries));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
