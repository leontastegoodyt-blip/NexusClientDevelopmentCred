package com.nexusclient.launcher.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Account – the Minecraft player the game is started as.
 *
 * Minecraft needs four things about the player when it starts:
 *
 * @param username    the player's name, e.g. "Steve"
 * @param uuid        the player's unique ID, written without dashes
 * @param accessToken a secret "pass" that proves to Minecraft servers that you own the account
 * @param userType    what kind of account it is: "msa" = Microsoft account
 */
public record Account(String username, String uuid, String accessToken, String userType) {

    /**
     * DEVELOPER MODE ONLY: a stand-in test player, used when nobody is signed in while running from
     * source (see DevMode). It lets the game be tested before Mojang approves Nexus's login.
     *
     * The game treats it as an offline player, so singleplayer works, but online servers refuse it.
     * Real releases never use this – Nexus never offers offline accounts to players (the project brief).
     */
    public static Account devPlaceholder() {
        String name = "NexusDev";
        // Minecraft's own rule for offline player IDs: a UUID made from "OfflinePlayer:<name>".
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new Account(name, uuid.toString().replace("-", ""), "0", "legacy");
    }
}
