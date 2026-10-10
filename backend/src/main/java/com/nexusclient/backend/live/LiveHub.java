package com.nexusclient.backend.live;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiveHub – keeps every player's live connection (WebSocket at /ws) and sends them events.
 *
 * A player is "online" while they have at least one connection (the launcher and the game can
 * both be connected). The client tells us which server it's on:
 *   {"type": "presence", "server": "mc.hypixel.net"}    ("" = not on a server)
 *   {"type": "ping"}                                     → {"type": "pong"} (keeps the line open)
 * Everything else a player does (send a DM, invite to a party…) goes through the normal /api/.
 */
@Component
public class LiveHub extends TextWebSocketHandler {

    /** WebSocket attribute holding the connected player's UUID (set by LiveSignIn). */
    public static final String PLAYER = "nexus.player";

    private static final int SEND_TIMEOUT_MS = 5000;
    private static final int BUFFER_LIMIT = 256 * 1024;

    private final Map<UUID, Set<WebSocketSession>> connections = new ConcurrentHashMap<>();
    private final Map<UUID, String> servers = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    public LiveHub(ApplicationEventPublisher events) {
        this.events = events;
    }

    // ------------------------------------------------------------------ asking

    public boolean isOnline(UUID player) {
        Set<WebSocketSession> set = connections.get(player);
        return set != null && !set.isEmpty();
    }

    /** How many players have the launcher open right now. */
    public int onlineCount() {
        return connections.size();
    }

    /** The server they're on ("" if none or offline). */
    public String serverOf(UUID player) {
        return isOnline(player) ? servers.getOrDefault(player, "") : "";
    }

    // ------------------------------------------------------------------ sending

    /** Sends an event to every connection of that player (does nothing if they're offline). */
    public void send(UUID player, Event event) {
        Set<WebSocketSession> set = connections.get(player);
        if (set == null) {
            return;
        }
        TextMessage message = new TextMessage(event.toJson());
        for (WebSocketSession session : set) {
            try {
                session.sendMessage(message);
            } catch (IOException | IllegalStateException e) {
                closeQuietly(session);
            }
        }
    }

    // ------------------------------------------------------------------ connections

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) {
        UUID player = (UUID) raw.getAttributes().get(PLAYER);
        // The decorator makes sending safe from many threads at once.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIMEOUT_MS, BUFFER_LIMIT);
        raw.getAttributes().put("nexus.decorated", session);
        boolean wasOnline = isOnline(player);
        connections.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet()).add(session);
        if (!wasOnline) {
            servers.put(player, "");
            events.publishEvent(new PresenceChanged(player, true, ""));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
        UUID player = (UUID) raw.getAttributes().get(PLAYER);
        WebSocketSession session = (WebSocketSession) raw.getAttributes().get("nexus.decorated");
        JsonObject json;
        try {
            json = JsonParser.parseString(message.getPayload()).getAsJsonObject();
        } catch (RuntimeException e) {
            return; // ignore anything that isn't a JSON object
        }
        String type = json.has("type") ? json.get("type").getAsString() : "";
        switch (type) {
            case "presence" -> {
                String server = json.has("server") && !json.get("server").isJsonNull()
                        ? cleanServer(json.get("server").getAsString()) : "";
                String old = servers.put(player, server);
                if (!server.equals(old)) {
                    events.publishEvent(new PresenceChanged(player, true, server));
                }
            }
            case "ping" -> {
                try {
                    session.sendMessage(new TextMessage(Event.of("pong").toJson()));
                } catch (IOException e) {
                    closeQuietly(session);
                }
            }
            default -> { } // unknown types are ignored (newer clients may send things we don't know yet)
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        UUID player = (UUID) raw.getAttributes().get(PLAYER);
        Object decorated = raw.getAttributes().get("nexus.decorated");
        Set<WebSocketSession> set = connections.get(player);
        if (set != null) {
            set.remove(decorated);
            if (set.isEmpty()) {
                connections.remove(player, set);
                servers.remove(player);
                events.publishEvent(new PresenceChanged(player, false, ""));
            }
        }
    }

    /** Kicks all of a player's connections (e.g. when they're banned). */
    public void disconnect(UUID player) {
        Set<WebSocketSession> set = connections.get(player);
        if (set != null) {
            set.forEach(this::closeQuietly);
        }
    }

    /** Server addresses are short and simple – anything else is ignored. */
    private static String cleanServer(String server) {
        String s = server.trim().toLowerCase();
        return s.length() <= 255 && s.matches("[a-z0-9.:_\\-\\[\\]]*") ? s : "";
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
