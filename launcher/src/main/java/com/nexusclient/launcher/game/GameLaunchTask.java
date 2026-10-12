package com.nexusclient.launcher.game;

import com.nexusclient.launcher.DevMode;
import com.nexusclient.launcher.account.Account;
import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.account.AccountManager.SavedAccount;
import com.nexusclient.launcher.account.LoginException;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.mods.ModManager;
import com.nexusclient.launcher.online.NexusOnline;
import com.nexusclient.launcher.settings.SettingsStore;
import com.nexusclient.launcher.tools.Playtime;
import com.nexusclient.launcher.tools.WorldBackups;
import com.nexusclient.launcher.torcherrig.TorcherRig;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.List;

/**
 * GameLaunchTask – everything that happens after clicking LAUNCH, as one background job:
 *
 *   1. sign in with the active account (AccountManager)
 *   2. download the game files         (GameInstaller)
 *   3. download the right Java         (JavaRuntimes)
 *   4. build the start-up command      (LaunchCommand)
 *   5. start Minecraft and wait until it's closed
 *
 * It's a JavaFX "Task", so it runs on a background thread (the window never freezes), and its
 * message and progress can be shown straight in the Launch bar. When the game closes, the task
 * finishes with the game's "exit code": 0 means it closed normally, anything else means it crashed.
 */
public class GameLaunchTask extends Task<Integer> {

    private final Instance instance;
    private final MinecraftVersion version; // the actual version to play (for "Latest": the newest)
    private final AccountManager accounts;
    private final SavedAccount player;      // the account to play as (null = nobody signed in)
    private final QuickPlay quickPlay;      // a server/world to go straight to (null = title screen)

    /** The file where the game's output is saved – useful for finding out why it crashed. */
    private Path outputLog;

    // The running game, once started. "volatile" because the STOP button reads it from the UI thread.
    private volatile Process game;
    private volatile boolean stoppedByPlayer = false;

    /** Called (on the UI thread) the moment the game has started. */
    private Runnable onGameStarted;

    /** Safe mode: start without any mods (as plain Vanilla) to check whether a mod causes problems. */
    private boolean safeMode = false;

    /** Extra logging: asks the game and Fabric to write more detail, to help find a crash's cause. */
    private boolean extraLogging = false;

    public GameLaunchTask(Instance instance, MinecraftVersion version, AccountManager accounts, SavedAccount player,
                          QuickPlay quickPlay) {
        this.instance = instance;
        this.version = version;
        this.accounts = accounts;
        this.player = player;
        this.quickPlay = quickPlay;
    }

    public Path outputLog() {
        return outputLog;
    }

    /** Safe mode: the instance starts as plain Vanilla, so no mods load. Its mods folder isn't touched. */
    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }

    /** Run with extra logging (Game Error Scan → "Run again with extra logging"). */
    public void setExtraLogging(boolean extraLogging) {
        this.extraLogging = extraLogging;
    }

    /** Sets what to do when the game has started (e.g. hide the launcher, show Discord status). */
    public void setOnGameStarted(Runnable action) {
        onGameStarted = action;
    }

    /** True if the player pressed STOP. */
    public boolean wasStoppedByPlayer() {
        return stoppedByPlayer;
    }

    /**
     * The STOP button: force-closes the game (like ending it in Task Manager), or, if the game
     * hasn't started yet, cancels the downloads.
     */
    public void stop() {
        stoppedByPlayer = true;
        Process running = game;
        if (running != null) {
            running.destroyForcibly();
        } else {
            cancel(); // stops the download thread
        }
    }

    @Override
    protected Integer call() throws Exception {
        // Send progress from the downloads to this task's message and progress (shown in the UI).
        ProgressListener progress = (message, fraction) -> {
            updateMessage(message);
            updateProgress(fraction < 0 ? -1 : fraction, 1);
        };

        // Sign in first, so a sign-in problem shows up straight away (before any big downloads).
        Account account = signIn();

        Path gameDir = NexusPaths.instanceDir(instance).toAbsolutePath();
        Files.createDirectories(gameDir);

        List<String> command;
        try (Downloader downloader = new Downloader()) {
            // Vanilla instances may secretly start through Fabric so the Nexus mod works (see
            // Instance.launchLoader). Safe mode is always truly Vanilla.
            ModManager mods = new ModManager(instance, version.id());
            Path hiddenNexusMods = null;
            if (!safeMode && instance.secretlyFabric(version.id())) {
                hiddenNexusMods = mods.prepareHiddenNexusMods(progress); // null = couldn't, play plain Vanilla
            }
            String loader;
            if (safeMode) {
                loader = Instance.VANILLA;
            } else if (instance.secretlyFabric(version.id())) {
                loader = hiddenNexusMods != null ? Instance.FABRIC : Instance.VANILLA;
            } else {
                loader = instance.loaderOrVanilla();
            }
            GameInstaller.InstalledGame game = GameInstaller.install(version, loader,
                    instance.loaderVersion(), gameDir, downloader, progress);

            VersionJson.JavaVersion javaVersion = game.version().javaVersion();
            // Every version's JSON says which Java it needs; very old ones might not, so use Java 8.
            String component = javaVersion != null ? javaVersion.component() : "jre-legacy";
            int major = javaVersion != null ? javaVersion.majorVersion() : 8;
            Path java = JavaRuntimes.ensure(component, major, downloader, progress);

            // If TorcherRig's settings are waiting for this instance's first launch, apply them now.
            TorcherRig.applyPendingBeforeLaunch(instance, version.id());

            // Fabric instances get Nexus's performance mods (Sodium, Lithium, FerriteCore) the first
            // time they launch. If the player removes them later, they're not added back.
            // They also get the in-game Nexus mod (Right Shift menu, HUD…) on versions it's built for.
            if (!safeMode) {
                mods.ensurePerformanceMods(progress);
                mods.ensureNexusMod(progress);
            }

            // The instance's own Java options can be empty (null) – that must become "", never the word "null".
            String customJvmArgs = instance.jvmArgs() == null ? "" : instance.jvmArgs();
            // Tell the in-game Nexus mod this PC's TorcherRig tier (shown on the frame-time graph).
            String tier = SettingsStore.get().torcherTier;
            if (tier != null) {
                customJvmArgs += " -Dnexus.torcherTier=" + tier.charAt(0) + tier.substring(1).toLowerCase(Locale.ROOT);
            }
            // …and TorcherRig's DFR advice, which the game applies once (step 16).
            String dfrAdvice = SettingsStore.get().dfrRecommendation;
            if (dfrAdvice != null) {
                customJvmArgs += " -Dnexus.dfrRecommendation=" + dfrAdvice;
            }
            // The in-game Nexus text in the player's language (same translation files as the launcher).
            Path langFile = translationFile(SettingsStore.get().language);
            if (langFile != null) {
                customJvmArgs += " \"-Dnexus.langFile=" + langFile.toAbsolutePath() + "\"";
            }
            // Streamer mode in the launcher switches on the in-game Streamer Mode too.
            if (SettingsStore.get().streamerMode) {
                customJvmArgs += " -Dnexus.streamerMode=true";
            }
            // …and where the shared tools folder is (so FFmpeg for clips is downloaded only once).
            customJvmArgs += " \"-Dnexus.toolsDir=" + NexusPaths.tools() + "\"";
            // A secretly-Fabric Vanilla instance loads only the hidden Nexus mods folder, never its own.
            if (hiddenNexusMods != null) {
                customJvmArgs += " \"-Dfabric.modsFolder=" + hiddenNexusMods.toAbsolutePath() + "\"";
            }
            command = LaunchCommand.build(game, java, gameDir, account, chooseRamMb(major), customJvmArgs + loggingOptions(major),
                    quickPlay);
        }

        // Save the command and everything the game prints to a log file in the instance folder.
        updateMessage("Starting Minecraft " + version.id() + "…");
        updateProgress(-1, 1);
        Path logsDir = gameDir.resolve("logs");
        Files.createDirectories(logsDir);
        outputLog = logsDir.resolve("nexus-output.log");
        // The login pass is secret, so it's blanked out of the log.
        String secret = account.accessToken();
        Files.writeString(outputLog, "Nexus launch command:\n" + hideSecret(String.join(" ", command), secret) + "\n\n");

        if (isCancelled()) {
            return -1; // STOP was pressed while downloading
        }
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(gameDir.toFile())               // the game runs inside the instance folder
                .redirectErrorStream(true);                // errors and normal output go to the same place
        // The in-game mod reports activity for clan honor with our Nexus sign-in. It's passed as an
        // environment variable (only this game can read it), never on the command line or in a file.
        NexusOnline nexus = NexusOnline.get();
        builder.environment().put("NEXUS_BACKEND", nexus.baseUrl()); // for Partner Server rules (public)
        if (nexus.signedIn() && !safeMode) {
            builder.environment().put("NEXUS_TOKEN", nexus.token());
        }
        game = builder.start();

        // Copy everything the game prints into the log, line by line, blanking out the login pass
        // (old Minecraft versions print it as part of their "Session ID").
        Process running = game;
        Thread copier = new Thread(() -> copyOutput(running, outputLog, secret), "game-output");
        copier.setDaemon(true);
        copier.start();

        if (onGameStarted != null) {
            javafx.application.Platform.runLater(onGameStarted);
        }
        updateMessage("Minecraft " + version.id() + " is running – have fun!");
        updateProgress(1, 1);
        long started = System.currentTimeMillis();
        // Friends see which server we're playing on (when we launch straight into one).
        NexusOnline online = NexusOnline.get();
        online.setPresence(quickPlay != null && quickPlay.server() != null ? quickPlay.server() : "");
        int exitCode = waitForExit(game);
        online.setPresence("");

        // After playing: add up the playtime, and back up the worlds that were played.
        long ended = System.currentTimeMillis();
        Playtime.record(instance.id(), instance.name(), version.id(), started, ended);
        // Mods and settings may have changed while playing: refresh the shared workspace (Mod Rerender).
        com.nexusclient.launcher.rerender.WorkspaceSharing.syncLater();
        long minutes = (ended - started) / 60_000;
        if (online.signedIn() && minutes > 0) {
            try {
                online.post("/api/me/playtime", java.util.Map.of("minutes", Math.min(minutes, 24 * 60)));
            } catch (Exception e) {
                System.err.println("Couldn't report playtime to Nexus: " + e.getMessage()); // not important
            }
        }
        updateMessage("Backing up your worlds…");
        updateProgress(-1, 1);
        WorldBackups.autoBackUp(instance, started);
        return exitCode;
    }

    /**
     * Picks the account to play as, and gets a fresh Minecraft pass for it.
     * If nobody is signed in, only developer mode may use the test player.
     */
    private Account signIn() throws LoginException, InterruptedException {
        if (player != null) {
            updateMessage("Signing in as " + player.username() + "…");
            updateProgress(-1, 1);
            return accounts.accountForLaunch(player);
        }
        if (DevMode.enabled()) {
            return Account.devPlaceholder(); // developer mode only – never in real releases
        }
        throw new LoginException("Sign in with your Microsoft account first – click \"Sign in\" in the top-right corner.");
    }

    /** Copies the game's output into the log file until the game closes. */
    private static void copyOutput(Process process, Path log, String secret) {
        try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedWriter file = Files.newBufferedWriter(log, StandardOpenOption.APPEND)) {
            String line;
            while ((line = output.readLine()) != null) {
                file.write(hideSecret(line, secret));
                file.newLine();
                file.flush(); // write straight away, so the log is complete even if something crashes
            }
        } catch (IOException e) {
            // The game closed or the file couldn't be written – nothing more to copy.
        }
    }

    /** Replaces the secret with stars wherever it appears. (Very short "secrets", like the test player's "0", are left alone.) */
    private static String hideSecret(String text, String secret) {
        if (secret == null || secret.length() < 8) {
            return text;
        }
        return text.replace(secret, "********");
    }

    /**
     * Extra Java options for "Run again with extra logging": Fabric writes more detail, and newer Java
     * explains exactly which value was missing when something goes wrong (Java 14+ only – Java 8
     * would refuse to start with it).
     */
    private String loggingOptions(int javaMajorVersion) {
        if (!extraLogging) {
            return "";
        }
        return " -Dfabric.log.level=debug" + (javaMajorVersion >= 14 ? " -XX:+ShowCodeDetailsInExceptionMessages" : "");
    }

    /** Waits until the player closes the game, and returns its exit code. */
    private static int waitForExit(Process process) throws IOException {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Stopped waiting for Minecraft", e);
        }
    }

    /**
     * How much memory to give the game: the instance's own setting if it has one, otherwise the
     * amount set by TorcherRig or the RAM slider.
     * Otherwise ("Auto"): 2 GB for old Java 8 versions, 4 GB for newer ones, but never more than
     * half of the computer's memory (and at least 1 GB).
     */
    private int chooseRamMb(int javaMajorVersion) {
        if (instance.ramMb() > 0) {
            return instance.ramMb(); // this instance has its own memory setting
        }
        int chosen = SettingsStore.get().ramMb;
        if (chosen > 0) {
            return chosen;
        }
        long totalMb = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize() / (1024 * 1024);
        int wanted = javaMajorVersion <= 8 ? 2048 : 4096;
        return (int) Math.max(1024, Math.min(wanted, totalMb / 2));
    }

    /**
     * The translation file for the game (null for English): the player's own file in
     * <Nexus folder>/lang/ if there is one, otherwise the launcher's built-in copy (copied out once).
     */
    private static Path translationFile(String language) {
        if (language == null || language.equals("en")) {
            return null;
        }
        String name = "strings_" + language + ".json";
        Path own = NexusPaths.root().resolve("lang").resolve(name);
        if (Files.isRegularFile(own)) {
            return own;
        }
        try (var in = GameLaunchTask.class.getResourceAsStream("/com/nexusclient/launcher/lang/" + name)) {
            if (in == null) {
                return null;
            }
            Path copy = NexusPaths.root().resolve("lang").resolve(".built-in").resolve(name);
            Files.createDirectories(copy.getParent());
            Files.write(copy, in.readAllBytes());
            return copy;
        } catch (IOException e) {
            return null;
        }
    }
}
