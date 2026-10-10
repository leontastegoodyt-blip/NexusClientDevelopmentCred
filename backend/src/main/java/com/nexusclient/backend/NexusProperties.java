package com.nexusclient.backend;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

/**
 * NexusProperties – Nexus's own settings, from application.yml (the "nexus:" section).
 *
 * @param devAuth   allow the test sign-in without Mojang (ONLY for development – never on the real server)
 * @param developers Minecraft UUIDs with the developer role – ONLY the Nexus owner (everything admins can do,
 *                   plus the launcher's Developer tab and in-game developer tools)
 * @param admins    Minecraft UUIDs of Nexus admins (can ban, kick, mute and handle reports)
 * @param moderators Minecraft UUIDs of moderators (can handle reports and mute)
 * @param sessionDays how long a sign-in lasts before the launcher must sign in again
 */
@ConfigurationProperties("nexus")
public record NexusProperties(boolean devAuth, List<UUID> developers, List<UUID> admins, List<UUID> moderators, int sessionDays) {

    public NexusProperties {
        developers = developers == null ? List.of() : developers;
        admins = admins == null ? List.of() : admins;
        moderators = moderators == null ? List.of() : moderators;
        sessionDays = sessionDays <= 0 ? 30 : sessionDays;
    }
}
