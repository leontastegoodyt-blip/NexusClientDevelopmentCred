package com.nexusclient.launcher.game;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.nexusclient.launcher.account.Account;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LaunchCommand – builds the full command that starts Minecraft, something like:
 *
 *   javaw.exe -Xmx2048M ...java options... -cp <all the .jars> net.minecraft.client.main.Main
 *             --username NexusDev --version 1.8.9 --gameDir ... --assetsDir ...
 *
 * Mojang's JSON gives the options as templates with placeholders like ${auth_player_name};
 * this class fills in the real values.
 */
public final class LaunchCommand {

    private static final String LAUNCHER_NAME = "NexusClient";
    private static final String LAUNCHER_VERSION = "0.1.0";

    /** Matches a placeholder like ${game_directory}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private static final Gson GSON = new Gson();
    private static final Type RULE_LIST = new TypeToken<List<VersionJson.Rule>>() {}.getType();

    private LaunchCommand() {}

    /**
     * @param game    the installed version
     * @param java    the java program to run it with
     * @param gameDir the instance's game folder
     * @param account the player to start as
     * @param ramMb   the most memory (RAM) the game may use, in megabytes
     * @param extraJvmArgs the player's own extra Java options (from instance settings; may be empty)
     * @param quickPlay    a server to join or world to open straight away, or null for the title screen
     */
    public static List<String> build(GameInstaller.InstalledGame game, Path java, Path gameDir,
                                     Account account, int ramMb, String extraJvmArgs, QuickPlay quickPlay) {
        VersionJson version = game.version();
        OperatingSystem os = OperatingSystem.current();

        List<String> classpath = new ArrayList<>();
        for (Path jar : game.classpath()) {
            classpath.add(jar.toString());
        }

        // The values for every ${placeholder} Mojang uses.
        Map<String, String> values = new HashMap<>();
        values.put("auth_player_name", account.username());
        values.put("auth_uuid", account.uuid());
        values.put("auth_access_token", account.accessToken());
        values.put("auth_xuid", "0");
        values.put("clientid", "");
        values.put("user_type", account.userType());
        values.put("user_properties", "{}");
        values.put("version_name", version.id());
        values.put("version_type", version.type());
        values.put("game_directory", gameDir.toString());
        values.put("assets_root", game.assetsRoot().toString());
        values.put("game_assets", game.gameAssetsDir().toString());
        values.put("assets_index_name", version.assetIndex().id());
        values.put("natives_directory", game.nativesDir().toString());
        values.put("library_directory", NexusPaths.libraries().toString());
        values.put("classpath", String.join(File.pathSeparator, classpath));
        values.put("classpath_separator", File.pathSeparator);
        values.put("launcher_name", LAUNCHER_NAME);
        values.put("launcher_version", LAUNCHER_VERSION);

        // Quick Play: newer versions have official options for "join this server" / "open this world",
        // switched on with these "features".
        Set<String> features = new HashSet<>();
        if (quickPlay != null && quickPlay.server() != null) {
            features.add("is_quick_play_multiplayer");
            values.put("quickPlayMultiplayer", quickPlay.server());
        }
        if (quickPlay != null && quickPlay.world() != null) {
            features.add("is_quick_play_singleplayer");
            values.put("quickPlaySingleplayer", quickPlay.world());
        }

        List<String> command = new ArrayList<>();
        command.add(java.toString());

        // --- Java options ---
        command.add("-Xmx" + ramMb + "M"); // the most memory the game may use
        // The memory clean-up settings Mojang's own launcher uses (keeps stutters short).
        command.addAll(List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20",
                "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M"));
        // Log4Shell protection: Mojang's fixed logging settings, plus an extra off-switch.
        command.add("-Dlog4j2.formatMsgNoLookups=true");
        if (game.logConfig() != null) {
            command.add(version.logging().client().argument().replace("${path}", game.logConfig().toString()));
        }
        // The player's own extra Java options (advanced players only).
        command.addAll(splitArguments(extraJvmArgs));

        if (version.arguments() != null && version.arguments().jvm() != null) {
            command.addAll(resolve(version.arguments().jvm(), values, features)); // newer versions list them
        } else {
            // Older versions don't list Java options, so we add the ones the official launcher uses.
            if (os == OperatingSystem.MAC) {
                command.add("-XstartOnFirstThread");
            }
            if (os == OperatingSystem.WINDOWS) {
                command.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
            }
            command.add("-Djava.library.path=" + game.nativesDir());
            command.add("-cp");
            command.add(values.get("classpath"));
        }

        // --- The class that starts the game ---
        command.add(version.mainClass());

        // --- Game options ---
        boolean hasQuickPlay = false;
        if (version.arguments() != null && version.arguments().game() != null) {
            command.addAll(resolve(version.arguments().game(), values, features));
            hasQuickPlay = version.arguments().game().toString().contains("is_quick_play_multiplayer");
        } else {
            for (String part : version.minecraftArguments().split(" ")) {
                command.add(fill(part, values));
            }
        }
        // Older versions (like 1.8.9) have no Quick Play, but can still join a server straight away.
        if (quickPlay != null && quickPlay.server() != null && !hasQuickPlay) {
            String server = quickPlay.server();
            int colon = server.lastIndexOf(':');
            command.add("--server");
            command.add(colon > 0 ? server.substring(0, colon) : server);
            command.add("--port");
            command.add(colon > 0 ? server.substring(colon + 1) : "25565");
        }
        return command;
    }

    /**
     * Turns Mojang's list of options into plain text. Each item is either text, or an object with
     * rules and a value – which is only included if the rules allow it on this computer.
     */
    private static List<String> resolve(List<JsonElement> arguments, Map<String, String> values, Set<String> features) {
        List<String> result = new ArrayList<>();
        for (JsonElement argument : arguments) {
            if (argument.isJsonPrimitive()) {
                result.add(fill(argument.getAsString(), values));
                continue;
            }
            JsonObject conditional = argument.getAsJsonObject();
            List<VersionJson.Rule> rules = GSON.fromJson(conditional.get("rules"), RULE_LIST);
            if (!Rules.allows(rules, features)) {
                continue;
            }
            JsonElement value = conditional.get("value");
            if (value.isJsonArray()) {
                for (JsonElement part : value.getAsJsonArray()) {
                    result.add(fill(part.getAsString(), values));
                }
            } else {
                result.add(fill(value.getAsString(), values));
            }
        }
        return result;
    }

    /**
     * Splits a line of options into separate ones at the spaces, keeping "quoted parts" together:
     *   -Xss2M "-Dsome.path=C:\My Folder"  →  [-Xss2M, -Dsome.path=C:\My Folder]
     */
    static List<String> splitArguments(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    /** Replaces every ${placeholder} in the text with its value. Unknown ones are left as they are. */
    private static String fill(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
