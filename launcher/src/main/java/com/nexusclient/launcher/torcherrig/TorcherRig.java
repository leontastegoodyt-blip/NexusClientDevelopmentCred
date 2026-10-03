package com.nexusclient.launcher.torcherrig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TorcherRig – Nexus's hardware optimiser (the project brief section 3).
 *
 *   1. HardwareScanner reads the PC (and Benchmark optionally measures its real speed).
 *   2. classify() sorts the PC into a Tier: Low, Mid, High or Ultra.
 *   3. plan() decides what to change for that tier, as a list the player can read first.
 *   4. apply() changes it – only after the player says yes – backing up the old settings first.
 *   5. revert() puts the old settings back.
 *
 * Game settings go into each instance's options.txt. An instance that has never been played has no
 * options.txt yet, so its change is marked "pending" and done just before its first launch (when the
 * game files are downloaded and we know the exact settings format).
 */
public final class TorcherRig {

    /**
     * Everything TorcherRig recommends.
     *
     * @param tier     the PC's tier
     * @param ramMb    memory to give the game
     * @param game     in-game video settings
     * @param changes  the plain-English list shown to the player before applying
     * @param tips     extra advice (e.g. "close other programs")
     */
    public record Plan(Tier tier, int ramMb, GameSettingsPlan game, boolean performanceMods,
                       boolean shadersOff, String dfr, List<Change> changes, List<String> tips,
                       String hardwareFingerprint) {}

    /** One line in the "what will change" list, e.g. ("Render distance", "6 chunks"). */
    public record Change(String setting, String value) {}

    // ---- the backup record, saved in torcherrig.json so "Revert" works even after a restart
    private record InstanceState(String status, String backupFile) {}
    private record State(int previousRamMb, String previousTier, GameSettingsPlan game,
                         Map<String, InstanceState> instances) {}

    private static final String APPLIED = "APPLIED";
    private static final String PENDING = "PENDING";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Path STATE_FILE = NexusPaths.root().resolve("torcherrig.json");

    private TorcherRig() {}

    // ================================================================== 2. tiers

    /**
     * Sorts the PC into a tier. The final tier is the WEAKEST of the processor and graphics tiers
     * (a fast CPU can't make up for weak graphics, and the other way round), and low RAM caps it.
     *
     * @param benchmark the speed test result, or null if the player skipped it
     */
    public static Tier classify(HardwareInfo hardware, Benchmark.Result benchmark) {
        Tier tier = Tier.min(cpuTier(hardware, benchmark), gpuTier(hardware));
        double ramGb = hardware.totalRamMb() / 1024.0;
        if (ramGb < 9) {           // "8 GB" PCs report a bit under 8
            tier = Tier.min(tier, Tier.LOW);
        } else if (ramGb < 15) {   // 12 GB
            tier = Tier.min(tier, Tier.MID);
        } else if (ramGb < 24) {   // 16 GB
            tier = Tier.min(tier, Tier.HIGH);
        }
        return tier;
    }

    private static Tier cpuTier(HardwareInfo hardware, Benchmark.Result benchmark) {
        if (benchmark != null) {
            // Minecraft leans on one core the most, so that score counts for more.
            double score = benchmark.singleCore() * 0.7 + benchmark.multiCore() * 0.3;
            if (score >= 140) return Tier.ULTRA;
            if (score >= 100) return Tier.HIGH;
            if (score >= 55) return Tier.MID;
            return Tier.LOW;
        }
        // No speed test: judge by thread count and speed.
        int threads = hardware.cpuThreads();
        double ghz = hardware.cpuGhz();
        if (threads >= 16 && ghz >= 3.5) return Tier.ULTRA;
        if (threads >= 12 && ghz >= 3.0) return Tier.HIGH;
        if (threads >= 6) return Tier.MID;
        return Tier.LOW;
    }

    /** Judges the best graphics card by its name and video memory. */
    private static Tier gpuTier(HardwareInfo hardware) {
        Tier best = Tier.LOW;
        for (HardwareInfo.Gpu gpu : hardware.gpus()) {
            best = best.ordinal() >= gpuTier(gpu).ordinal() ? best : gpuTier(gpu);
        }
        return best;
    }

    private static Tier gpuTier(HardwareInfo.Gpu gpu) {
        String name = gpu.name().toLowerCase(Locale.ROOT);
        long vramGb = gpu.vramMb() / 1024;

        // Top-end cards.
        if (name.matches(".*rtx\\s*(4080|4090|5070 ti|5080|5090).*") || name.matches(".*rx\\s*(7900|9070).*")) {
            return Tier.ULTRA;
        }
        // Built-in ("integrated") graphics that share the PC's memory.
        boolean integrated = (name.contains("intel") && !name.contains("arc"))
                || name.equals("amd radeon(tm) graphics") || name.contains("vega")
                || name.matches(".*radeon\\s*\\d{3}m.*");
        if (integrated) {
            // Intel Iris Xe and newer AMD built-in graphics can manage Mid; older ones are Low.
            return name.contains("iris") || name.matches(".*radeon\\s*(7[68]0m|8[68]0m|890m).*") ? Tier.MID : Tier.LOW;
        }
        if (name.startsWith("apple m")) {
            return name.contains("pro") || name.contains("max") || name.contains("ultra") ? Tier.HIGH : Tier.MID;
        }
        // Separate ("discrete") graphics cards: go by video memory.
        if (vramGb >= 12) return Tier.ULTRA;
        if (vramGb >= 6) return Tier.HIGH;
        if (vramGb >= 3) return Tier.MID;
        return Tier.LOW;
    }

    // ================================================================== 3. the plan

    /** Decides what to change for this PC. Nothing is changed until apply() is called. */
    public static Plan plan(HardwareInfo hardware, Benchmark.Result benchmark) {
        Tier tier = classify(hardware, benchmark);
        int refresh = Math.max(30, Math.min(hardware.refreshRate(), 260));

        // Memory: more for stronger PCs, but always leave room for Windows and other programs.
        int wanted = switch (tier) {
            case LOW -> 3072;
            case MID -> 4096;
            case HIGH -> 6144;
            case ULTRA -> 8192;
        };
        long safeMax = Math.min(hardware.totalRamMb() / 2, hardware.totalRamMb() - 3072);
        int ramMb = (int) Math.max(1024, Math.min(wanted, (safeMax / 512) * 512));

        GameSettingsPlan game = switch (tier) {
            case LOW -> new GameSettingsPlan(6, 5, "FAST", "MINIMAL", "OFF", false, false, refresh);
            case MID -> new GameSettingsPlan(10, 8, "FANCY", "DECREASED", "FAST", true, true, refresh);
            case HIGH -> new GameSettingsPlan(16, 10, "FANCY", "ALL", "FANCY", true, true, 260);
            case ULTRA -> new GameSettingsPlan(24, 12, "FABULOUS", "ALL", "FANCY", true, true, 260);
        };
        boolean performanceMods = tier == Tier.LOW || tier == Tier.MID;
        boolean shadersOff = tier == Tier.LOW || tier == Tier.MID;
        String dfr = switch (tier) {
            case LOW -> "OFF";
            case MID -> "LOW";
            case HIGH -> "MEDIUM";
            case ULTRA -> "HIGH";
        };

        List<Change> changes = new ArrayList<>();
        changes.add(new Change("Memory (RAM) for the game", formatGb(ramMb)));
        changes.add(new Change("Render distance", game.renderDistance() + " chunks"));
        changes.add(new Change("Simulation distance", game.simulationDistance() + " chunks (not in 1.8.9)"));
        changes.add(new Change("Graphics", capitalise(game.graphics())));
        changes.add(new Change("Particles", capitalise(game.particles())));
        changes.add(new Change("Clouds", capitalise(game.clouds())));
        changes.add(new Change("Entity shadows", game.entityShadows() ? "On" : "Off"));
        changes.add(new Change("Smooth lighting", game.smoothLighting() ? "On" : "Off"));
        changes.add(new Change("Max FPS", game.maxFps() >= 260 ? "Unlimited" : game.maxFps() + " (your screen's refresh rate)"));
        changes.add(new Change("Performance mods", performanceMods
                ? "Light performance mods (added automatically once mod support arrives)" : "Your choice"));
        changes.add(new Change("Shaders", shadersOff ? "Off by default" : "Allowed"));
        changes.add(new Change("DFR (Dual Frame Reset)", switch (dfr) {
            case "OFF" -> "Not recommended on this PC";
            case "LOW" -> "Recommended at low strength";
            case "MEDIUM" -> "Recommended at medium strength";
            default -> "Recommended at high strength";
        }));

        List<String> tips = new ArrayList<>();
        if (hardware.freeRamMb() < 1536) {
            tips.add("Only " + formatGb((int) hardware.freeRamMb()) + " of memory is free right now. "
                    + "Close other programs (especially web browsers) before playing for smoother gameplay.");
        }
        if (tier == Tier.LOW) {
            tips.add("On a laptop, plug in the charger – laptops slow down on battery.");
        }
        return new Plan(tier, ramMb, game, performanceMods, shadersOff, dfr, changes, tips, hardware.fingerprint());
    }

    // ================================================================== 4. apply

    /**
     * Applies a plan to some instances (only after the player said yes). Backs up everything first.
     *
     * @param targets    the instances to change (one, or all of them)
     * @param versionIds each target's Minecraft version (e.g. "1.8.9"), in the same order
     */
    public static void apply(Plan plan, List<Instance> targets, List<String> versionIds) throws IOException {
        Settings settings = SettingsStore.get();
        State previous = loadState();

        Map<String, InstanceState> instances = new LinkedHashMap<>();
        String stamp = String.valueOf(System.currentTimeMillis());
        for (int i = 0; i < targets.size(); i++) {
            Instance instance = targets.get(i);
            Path dir = NexusPaths.instanceDir(instance);
            Path options = dir.resolve("options.txt");
            if (Files.exists(options)) {
                // Back up the current settings, then change them.
                Path backup = dir.resolve("nexus-backups").resolve("options-before-torcherrig-" + stamp + ".txt");
                Files.createDirectories(backup.getParent());
                Files.copy(options, backup, StandardCopyOption.REPLACE_EXISTING);
                OptionsFile.apply(options, plan.game(), versionIds.get(i), clientJar(versionIds.get(i)));
                instances.put(instance.id(), new InstanceState(APPLIED, backup.toString()));
            } else {
                // Never played yet: change it just before its first launch.
                instances.put(instance.id(), new InstanceState(PENDING, null));
            }
        }

        // Remember what to go back to. If TorcherRig was already applied, keep the ORIGINAL RAM.
        int previousRam = previous != null ? previous.previousRamMb() : settings.ramMb;
        String previousTier = previous != null ? previous.previousTier() : settings.torcherTier;
        saveState(new State(previousRam, previousTier, plan.game(), instances));

        settings.ramMb = plan.ramMb();
        settings.torcherTier = plan.tier().name();
        settings.hardwareFingerprint = plan.hardwareFingerprint();
        settings.preferPerformanceMods = plan.performanceMods();
        settings.shadersOffByDefault = plan.shadersOff();
        settings.dfrRecommendation = plan.dfr();
        settings.optimisePromptPending = false;
        SettingsStore.save();
    }

    /**
     * Called just before an instance launches: if TorcherRig's change for it is still pending (it had
     * never been played), apply it now that the game files are downloaded.
     */
    public static void applyPendingBeforeLaunch(Instance instance, String versionId) {
        State state = loadState();
        if (state == null || state.instances() == null) {
            return;
        }
        InstanceState mine = state.instances().get(instance.id());
        if (mine == null || !PENDING.equals(mine.status())) {
            return;
        }
        try {
            Path dir = NexusPaths.instanceDir(instance);
            Path options = dir.resolve("options.txt");
            String backup = null;
            if (Files.exists(options)) {
                Path backupFile = dir.resolve("nexus-backups").resolve("options-before-torcherrig-"
                        + System.currentTimeMillis() + ".txt");
                Files.createDirectories(backupFile.getParent());
                Files.copy(options, backupFile, StandardCopyOption.REPLACE_EXISTING);
                backup = backupFile.toString();
            }
            OptionsFile.apply(options, state.game(), versionId, clientJar(versionId));
            state.instances().put(instance.id(), new InstanceState(APPLIED, backup));
            saveState(state);
        } catch (IOException e) {
            e.printStackTrace(); // not fatal: the game just starts with its normal settings
        }
    }

    // ================================================================== 5. revert

    /** True if there's a TorcherRig change that can be undone. */
    public static boolean canRevert() {
        return Files.exists(STATE_FILE);
    }

    /**
     * Puts back the settings from before TorcherRig ran. (Game settings changed in-game since then
     * are undone too.)
     */
    public static void revert(List<Instance> allInstances) throws IOException {
        State state = loadState();
        if (state == null) {
            return;
        }
        for (Instance instance : allInstances) {
            InstanceState mine = state.instances() == null ? null : state.instances().get(instance.id());
            if (mine == null || !APPLIED.equals(mine.status())) {
                continue; // pending changes were never made, so there's nothing to undo
            }
            Path options = NexusPaths.instanceDir(instance).resolve("options.txt");
            if (mine.backupFile() != null && Files.exists(Path.of(mine.backupFile()))) {
                Files.copy(Path.of(mine.backupFile()), options, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // There was no options.txt before, so remove ours: Minecraft goes back to its defaults.
                Files.deleteIfExists(options);
            }
        }
        Settings settings = SettingsStore.get();
        settings.ramMb = state.previousRamMb();
        settings.torcherTier = state.previousTier();
        settings.preferPerformanceMods = false;
        settings.shadersOffByDefault = false;
        settings.dfrRecommendation = null;
        SettingsStore.save();
        Files.deleteIfExists(STATE_FILE);
    }

    // ================================================================== helpers

    /** The downloaded game file for a version (may not exist yet). */
    private static Path clientJar(String versionId) {
        return NexusPaths.versions().resolve(versionId).resolve(versionId + ".jar");
    }

    private static State loadState() {
        try {
            return Files.exists(STATE_FILE) ? GSON.fromJson(Files.readString(STATE_FILE), State.class) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveState(State state) throws IOException {
        Files.createDirectories(STATE_FILE.getParent());
        Files.writeString(STATE_FILE, GSON.toJson(state));
    }

    /** 3072 → "3 GB", 3584 → "3.5 GB". */
    public static String formatGb(int megabytes) {
        double gb = megabytes / 1024.0;
        return (gb == Math.floor(gb) ? String.valueOf((int) gb) : String.format(Locale.ROOT, "%.1f", gb)) + " GB";
    }

    private static String capitalise(String text) {
        return text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
