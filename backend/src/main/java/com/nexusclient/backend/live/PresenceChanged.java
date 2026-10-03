package com.nexusclient.backend.live;

import java.util.UUID;

/**
 * PresenceChanged – announced inside the backend when a player comes online, goes offline, or
 * switches server. FriendService hears it and tells that player's friends.
 *
 * @param server the server address, or "" when not on a server (menus / singleplayer)
 */
public record PresenceChanged(UUID player, boolean online, String server) {}
