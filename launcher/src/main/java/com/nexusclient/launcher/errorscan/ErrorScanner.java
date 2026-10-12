package com.nexusclient.launcher.errorscan;

import com.google.gson.Gson;
import com.nexusclient.launcher.game.Downloader;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ErrorScanner – the Game Error Scan's detective (the project brief: Game Error Scan).
 *
 *   - preLaunchCheck(): BEFORE the game starts, looks for mods that need a missing mod, mods that
 *     break each other, the same mod installed twice, and damaged game files.
 *   - analyseCrash(): AFTER a crash, reads the logs and crash report and matches them against the
 *     list of known errors (data/known-errors.json). If nothing matches, it makes a best guess.
 */
public final class ErrorScanner {

    // The shape of known-errors.json.
    private record KnownError(String id, List<String> patterns, String title, String explanation, String fix,
                              Integer modGroup, Integer fallbackModGroup, Integer fileGroup, String fixLabel) {}
    private record KnownErrors(List<KnownError> errors) {}

    /** Mod ids that are always there in a Fabric game (Minecraft itself, Java, the loader). */
    private static final Set<String> BUILT_IN = Set.of("minecraft", "java", "fabricloader", "fabric-loader", "forge",
            "neoforge", "fml", "javafml",
            "mixinextras");

    private static List<KnownError> known;

    private ErrorScanner() {}

    private static synchronized List<KnownError> known() {
        if (known == null) {
            try (InputStream in = ErrorScanner.class.getResourceAsStream("/com/nexusclient/launcher/data/known-errors.json")) {
                known = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), KnownErrors.class).errors();
            } catch (Exception e) {
                e.printStackTrace();
                known = List.of();
            }
        }
        return known;
    }

    // ================================================================== before the game starts

    /**
     * Checks an instance before it starts. Fast (reads the mod files' own information, no internet).
     *
     * @param versionId the Minecraft version it plays (to check the game file isn't damaged)
     */
    public static List<Diagnosis> preLaunchCheck(Instance instance, String versionId) {
        List<Diagnosis> problems = new ArrayList<>();
        if (Instance.FABRIC.equals(instance.loaderOrVanilla())) {
            checkMods(instance, problems);
        }
        checkGameFile(versionId, problems);
        // The same problem can be spotted twice (e.g. by two copies of a mod) – show it once.
        java.util.Set<String> seen = new java.util.HashSet<>();
        problems.removeIf(problem -> !seen.add(problem.errorId() + "|" + problem.fixArg()));
        return problems;
    }

    /** Missing dependencies, mods that break each other, and duplicates. */
    private static void checkMods(Instance instance, List<Diagnosis> problems) {
        List<ModInfo> mods = installedMods(instance);
        Map<String, ModInfo> providers = new HashMap<>();
        Map<String, List<ModInfo>> byId = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            mod.provides().forEach(id -> providers.putIfAbsent(id, mod));
            byId.computeIfAbsent(mod.id(), id -> new ArrayList<>()).add(mod);
        }
        for (ModInfo mod : mods) {
            for (String needed : mod.depends()) {
                if (!BUILT_IN.contains(needed) && !providers.containsKey(needed)) {
                    problems.add(new Diagnosis("missing-dependency", "A mod needs another mod that isn't installed",
                            "The mod \"" + mod.name() + "\" needs \"" + needed + "\" to work, but it isn't installed.",
                            "INSTALL_DEPENDENCY", needed, "Install \"" + needed + "\" from Modrinth",
                            mod.file() + " depends on " + needed));
                }
            }
            for (Map.Entry<String, List<String>> broken : mod.breaks().entrySet()) {
                ModInfo other = providers.get(broken.getKey());
                // Only a problem if the other mod's version is one of the broken ones (e.g. "<0.5.4").
                if (other != null && other != mod && other.id().equals(broken.getKey())
                        && VersionRange.matchesAny(other.version(), broken.getValue())) {
                    problems.add(new Diagnosis("incompatible-mod", "Two mods don't work together",
                            "\"" + mod.name() + "\" doesn't work together with \"" + other.name() + "\".",
                            "DISABLE_MOD", mod.id(), "Switch off \"" + mod.name() + "\"",
                            mod.file() + " breaks " + other.id() + " " + String.join(" or ", broken.getValue())));
                }
            }
        }
        for (Map.Entry<String, List<ModInfo>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                problems.add(new Diagnosis("duplicate-mod", "The same mod is installed twice",
                        "\"" + entry.getValue().get(0).name() + "\" is in the mods folder " + entry.getValue().size()
                                + " times (probably different versions).",
                        "DISABLE_DUPLICATE", entry.getKey(), "Switch off the older copies",
                        String.join(", ", entry.getValue().stream().map(ModInfo::file).toList())));
            }
        }
    }

    /** Checks the downloaded game file against Mojang's fingerprint for it. */
    private static void checkGameFile(String versionId, List<Diagnosis> problems) {
        Path versionDir = NexusPaths.versions().resolve(versionId);
        Path jar = versionDir.resolve(versionId + ".jar");
        Path json = versionDir.resolve(versionId + ".json");
        if (!Files.exists(jar) || !Files.exists(json)) {
            return; // not downloaded yet – it will be downloaded (and checked) on launch
        }
        try {
            String expected = com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject()
                    .getAsJsonObject("downloads").getAsJsonObject("client").get("sha1").getAsString();
            if (!expected.equalsIgnoreCase(Downloader.sha1(jar))) {
                problems.add(new Diagnosis("corrupt-files", "Game files are damaged",
                        "The Minecraft " + versionId + " game file is damaged (its fingerprint doesn't match Mojang's).",
                        "REDOWNLOAD_FILES", versionId, "Re-download the game files", jar.toString()));
            }
        } catch (Exception e) {
            // can't check – not a problem worth bothering the player with
        }
    }

    /** Every switched-on Fabric mod in the instance. */
    public static List<ModInfo> installedMods(Instance instance) {
        Path mods = NexusPaths.instanceDir(instance).resolve("mods");
        List<ModInfo> list = new ArrayList<>();
        if (!Files.isDirectory(mods)) {
            return list;
        }
        try (Stream<Path> files = Files.list(mods)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                ModInfo info = ModInfo.read(file);
                if (info != null) {
                    list.add(info);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ================================================================== after a crash

    /**
     * Reads the logs from the last session (and the crash report, if one was written since it started)
     * and explains what went wrong.
     */
    public static List<Diagnosis> analyseCrash(Instance instance, long sessionStartMillis) {
        String text = collectLogs(instance, sessionStartMillis);
        List<Diagnosis> found = analyseText(text);
        if (found.isEmpty()) {
            found.add(bestGuess(text));
        }
        return found;
    }

    /** Everything worth reading from the last session, as one piece of text. */
    public static String collectLogs(Instance instance, long sinceMillis) {
        Path dir = NexusPaths.instanceDir(instance);
        StringBuilder text = new StringBuilder();
        appendIfExists(text, dir.resolve("logs").resolve("nexus-output.log"));
        Path crashReports = dir.resolve("crash-reports");
        if (Files.isDirectory(crashReports)) {
            try (Stream<Path> reports = Files.list(crashReports)) {
                reports.filter(path -> path.toFile().lastModified() >= sinceMillis)
                        .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                        .ifPresent(report -> appendIfExists(text, report));
            } catch (IOException ignored) {
                // no crash report
            }
        }
        return text.toString();
    }

    /** Matches text against every known error. Returns what it found (without repeats). */
    public static List<Diagnosis> analyseText(String text) {
        List<Diagnosis> found = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (KnownError error : known()) {
            for (String pattern : error.patterns()) {
                Matcher matcher = Pattern.compile(pattern).matcher(text);
                if (!matcher.find()) {
                    continue;
                }
                String modOrFile = group(matcher, error.modGroup());
                if (modOrFile == null) {
                    modOrFile = group(matcher, error.fallbackModGroup());
                }
                if (modOrFile == null) {
                    modOrFile = group(matcher, error.fileGroup());
                }
                if (seen.add(error.id() + "|" + modOrFile)) {
                    found.add(new Diagnosis(error.id(), error.title(), fill(error.explanation(), matcher),
                            error.fix(), modOrFile, fill(error.fixLabel(), matcher), lineAround(text, matcher.start())));
                }
                break; // one match per known error is enough
            }
        }
        return found;
    }

    /**
     * No known error matched: pick the most telling line (the last "Caused by", or the first exception)
     * as Nexus's best guess.
     */
    private static Diagnosis bestGuess(String text) {
        String guess = null;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Caused by:")) {
                guess = trimmed; // keep going: the last "Caused by" is usually the real cause
            } else if (guess == null && trimmed.matches(".*(Exception|Error)(:|$).*") && !trimmed.startsWith("at ")) {
                guess = trimmed;
            }
        }
        return new Diagnosis("unknown", "Minecraft crashed",
                guess == null
                        ? "Nexus doesn't recognise this crash yet, and the log didn't say much. Try safe mode (Tools) to "
                        + "check whether a mod is causing it, or copy the error report and send it to Nexus support."
                        : "Nexus doesn't recognise this crash yet. Its best guess is this line from the log:\n\n"
                        + shorten(guess, 300),
                "NONE", null, "", guess == null ? "" : guess);
    }

    // ================================================================== error report

    /** A text report to copy or send to Nexus support: system details plus the end of the logs. */
    public static String errorReport(Instance instance, String versionId, String logs) {
        String[] lines = logs.split("\\R");
        int from = Math.max(0, lines.length - 250);
        StringBuilder report = new StringBuilder();
        report.append("=== Nexus Client error report ===\n");
        report.append("Instance: ").append(instance.name()).append(" (").append(versionId).append(", ")
                .append(instance.loaderLabel()).append(")\n");
        report.append("System: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch")).append(")\n");
        report.append("Mods: ");
        installedMods(instance).forEach(mod -> report.append(mod.id()).append(' ').append(mod.version()).append(", "));
        report.append("\n\n=== Last ").append(lines.length - from).append(" log lines ===\n");
        for (int i = from; i < lines.length; i++) {
            report.append(lines[i]).append('\n');
        }
        return report.toString();
    }

    // ================================================================== helpers

    private static String group(Matcher matcher, Integer index) {
        if (index == null || index > matcher.groupCount()) {
            return null;
        }
        return matcher.group(index);
    }

    /** Replaces {1}, {2}… with what the pattern captured. */
    private static String fill(String text, Matcher matcher) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (int i = 1; i <= matcher.groupCount(); i++) {
            result = result.replace("{" + i + "}", matcher.group(i) == null ? "?" : matcher.group(i));
        }
        return result;
    }

    private static String lineAround(String text, int position) {
        int start = text.lastIndexOf('\n', position) + 1;
        int end = text.indexOf('\n', position);
        return shorten(text.substring(start, end < 0 ? text.length() : end).trim(), 300);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static void appendIfExists(StringBuilder text, Path file) {
        try {
            if (Files.exists(file)) {
                text.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            try {
                // Logs can contain bytes that aren't valid text – read them leniently.
                text.append(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)).append('\n');
            } catch (IOException ignored) {
                // unreadable
            }
        }
    }
}
