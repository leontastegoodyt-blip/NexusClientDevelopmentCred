package com.nexusclient.launcher.settings;

/**
 * Settings – the launcher's own settings, saved in settings.json (see SettingsStore).
 *
 * This is a plain class with public fields (instead of a record) because settings change while
 * the launcher runs. New settings can be added at any time: older settings.json files simply
 * won't have them, and they start at the default value written here.
 */
public class Settings {

    // ---------------- first run (build step 4)

    /** True once the welcome screen and tutorial have been finished (or skipped). */
    public boolean firstRunComplete = false;

    /**
     * True if the player skipped both TorcherRig and the manual RAM prompt during the first run.
     * the project brief: then the prompt is shown again after they sign in.
     */
    public boolean optimisePromptPending = false;

    // ---------------- memory (RAM)

    /** How much memory (in MB) to give the game. 0 = let Nexus decide automatically. */
    public int ramMb = 0;

    // ---------------- TorcherRig (build step 4)

    /** The tier TorcherRig gave this PC ("LOW", "MID", "HIGH", "ULTRA"), or null if never run. */
    public String torcherTier = null;

    /**
     * A short description of the hardware when TorcherRig last ran (CPU + graphics + RAM).
     * If it changes, the player has new hardware, and Nexus suggests running TorcherRig again.
     */
    public String hardwareFingerprint = null;

    /** TorcherRig's advice for later features: install light performance mods (step 6). */
    public boolean preferPerformanceMods = false;

    /** TorcherRig's advice for later features: shaders off by default (step 6). */
    public boolean shadersOffByDefault = false;

    /** TorcherRig's DFR advice (step 16): "OFF", "LOW", "MEDIUM" or "HIGH". */
    public String dfrRecommendation = null;

    // ---------------- General (build step 7)

    /** What the launcher does when the game starts: "STAY_OPEN", "MINIMISE_TO_TRAY" or "HIDE". */
    public String launchBehaviour = "STAY_OPEN";

    /** Show "Playing on Nexus Client" in Discord. */
    public boolean discordPresence = true;

    /** Check for Nexus updates when the launcher opens (Nexus always asks before updating). */
    public boolean checkForUpdates = true;

    /** Send crash reports to Nexus automatically (opt-in – off unless the player switches it on). */
    public boolean sendCrashReports = false;

    // ---------------- Appearance (build step 7)

    /** The main neon colour, e.g. "#00E5FF". Null = the default cyan. */
    public String accentColor = null;

    /** A picture shown faintly behind the pages (copied into the Nexus folder). Null = none. */
    public String backgroundImage = null;

    // ---------------- Accessibility (build step 7)

    /** Turns off animations (buttons growing, pages sliding, glows breathing). */
    public boolean reducedMotion = false;

    /** Makes the text bigger everywhere. */
    public boolean biggerText = false;

    /** "OFF", "RED_GREEN" (protanopia/deuteranopia) or "BLUE_YELLOW" (tritanopia). */
    public String colourblindMode = "OFF";

    // ---------------- Tools (build step 8)

    /** Back up singleplayer worlds automatically after playing them. */
    public boolean autoBackups = true;

    /** How many automatic backups to keep for each world (older ones are deleted). */
    public int backupsToKeep = 5;

    // ---------------- Language (build step 7)

    /** The launcher's language, e.g. "en". */
    public String language = "en";
}
