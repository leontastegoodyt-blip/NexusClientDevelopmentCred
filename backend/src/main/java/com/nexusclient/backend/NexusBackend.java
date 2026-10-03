package com.nexusclient.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NexusBackend – starts the Nexus online server.
 *
 * The code is split into folders (packages) by feature:
 *   account/     signing in (checked with Mojang), players, sessions
 *   friends/     friend requests, friends list, blocking
 *   live/        the WebSocket connection: live notifications and online status
 *   messages/    direct messages (DMs)
 *   party/       parties, party chat, party waypoints, joining a server together
 *   profile/     profile cards and each player's own settings
 *   share/       short share codes (instance setups, HUD layouts, crosshairs)
 *   moderation/  reports, the chat filter, bans and mutes
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // for regular clean-ups (old sign-in codes, expired sessions)
public class NexusBackend {

    public static void main(String[] args) {
        SpringApplication.run(NexusBackend.class, args);
    }
}
