package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.i18n.Lang;

import java.util.List;

/**
 * Page – the list of tabs in the launcher's left-hand bar.
 *
 * An "enum" is a fixed list of options. Each tab has:
 *   - a title (shown at the top of its page) and a short label (shown under its icon in the bar)
 *   - an icon
 *   - a short description of what it does
 *   - the build-plan step (from the project brief) where it gets built
 *   - its sub-tabs (the row of smaller tabs across the top of the page)
 * To add a new tab, add a line here.
 */
public enum Page {

    HOME("Home", "Home", Icons.HOME,
            "Your instances, news and the Launch button.", 0,
            List.of()),
    MODS("Mods", "Mods", Icons.MODS,
            "Browse and install mods, resource packs and shaders from Modrinth in one click.", 6,
            List.of("Mods", "Resource Packs", "Shaders", "Modpacks", "Installed")),
    SERVERS("Servers", "Servers", Icons.SERVERS,
            "Featured servers, Nexus Partner Servers, live ping and one-click join.", 7,
            List.of("Featured", "Nexus Partners", "Favourites", "Recent")),
    FRIENDS("Friends", "Friends", Icons.FRIENDS,
            "See who's online and where, start a party, and chat with friends.", 13,
            List.of("Online", "All Friends", "Requests", "Parties", "Blocked")),
    CLANS("Clans", "Clans", Icons.CLANS,
            "Create or join a clan, earn honor by playing, and climb the leaderboard.", 14,
            List.of("My Clan", "Leaderboard", "Events", "Browse")),
    SKINS("Skins", "Skins", Icons.SKINS,
            "Upload and switch Minecraft skins with a 3D preview.", 8,
            List.of("My Skins", "Upload", "Capes", "Nexus Cosmetics")),
    ERROR_SCAN("Game Error Scan", "Errors", Icons.ERROR_SCAN,
            "Finds out why Minecraft crashed, explains it in plain English and offers to fix it.", 9,
            List.of("Scan", "History", "Game Logs")),
    TOOLS("Tools", "Tools", Icons.TOOLS,
            "Screenshots, world backups, safe mode, playtime stats and the storage cleaner.", 8,
            List.of("Screenshots", "World Backups", "Safe Mode", "Playtime", "Storage")),
    SETTINGS("Settings", "Settings", Icons.SETTINGS,
            "Theme, language, accessibility, Discord status and launcher behaviour.", 7,
            List.of("General", "Appearance", "Game", "TorcherRig", "Accessibility", "Language", "Premium", "About"));

    private final String title;
    private final String shortLabel;
    private final String icon;
    private final String description;
    private final int buildStep;
    private final List<String> tabs;

    Page(String title, String shortLabel, String icon, String description, int buildStep, List<String> tabs) {
        this.title = title;
        this.shortLabel = shortLabel;
        this.icon = icon;
        this.description = description;
        this.buildStep = buildStep;
        this.tabs = tabs;
    }

    /** The page's title in the player's language (from the translation files; English if missing). */
    public String title() { return Lang.t(key("title"), title); }
    public String shortLabel() { return Lang.t(key("short"), shortLabel); }
    public String icon() { return icon; }
    public String description() { return Lang.t(key("description"), description); }

    /** e.g. "page.error_scan.title" */
    private String key(String part) {
        return "page." + name().toLowerCase(java.util.Locale.ROOT) + "." + part;
    }
    public int buildStep() { return buildStep; }
    public List<String> tabs() { return tabs; }
}
