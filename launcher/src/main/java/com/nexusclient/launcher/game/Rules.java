package com.nexusclient.launcher.game;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules – checks Mojang's "rules" that say when a library or start-up option applies.
 *
 * Example: [{"action": "allow", "os": {"name": "osx"}}] means "only on Mac".
 *
 * How they work (same as the official launcher):
 *   - No rules at all → always applies.
 *   - Otherwise start at "not allowed", go through the rules in order, and every rule that matches
 *     this computer sets the answer to its action ("allow" or "disallow"). The last match wins.
 */
public final class Rules {

    private Rules() {}

    /** True if these rules allow the thing on this computer (with no optional features turned on). */
    public static boolean allows(List<VersionJson.Rule> rules) {
        return allows(rules, Set.of());
    }

    /**
     * True if these rules allow the thing on this computer.
     *
     * @param features optional launcher features that are turned on, e.g. "is_quick_play_multiplayer"
     *                 (others Mojang's rules can ask about: "is_demo_user", "has_custom_resolution")
     */
    public static boolean allows(List<VersionJson.Rule> rules, Set<String> features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (VersionJson.Rule rule : rules) {
            if (matches(rule, features)) {
                allowed = "allow".equals(rule.action());
            }
        }
        return allowed;
    }

    /** True if one rule's conditions all fit this computer. */
    private static boolean matches(VersionJson.Rule rule, Set<String> features) {
        VersionJson.OsRule os = rule.os();
        if (os != null) {
            if (os.name() != null && !os.name().equals(OperatingSystem.current().mojangName())) {
                return false;
            }
            if (os.arch() != null && !os.arch().equals(OperatingSystem.mojangArch())) {
                return false;
            }
            if (os.version() != null
                    && !Pattern.compile(os.version()).matcher(System.getProperty("os.version")).find()) {
                return false;
            }
        }
        if (rule.features() != null) {
            for (Map.Entry<String, Boolean> feature : rule.features().entrySet()) {
                boolean enabled = features.contains(feature.getKey());
                if (enabled != feature.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }
}
