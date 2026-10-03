package com.nexusclient.backend.live;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Event – one live update sent to a player over the WebSocket, as JSON: {"type": "...", ...}.
 *
 * Types the launcher and mod understand:
 *   friend.request, friend.accepted, friend.removed, friend.presence
 *   dm
 *   party.invite, party.update, party.left, party.chat, party.waypoint, party.warp
 *   notice (a message from Nexus, e.g. "You've been muted")
 */
public final class Event {

    private static final Gson GSON = new Gson();
    private final JsonObject json = new JsonObject();

    private Event(String type) {
        json.addProperty("type", type);
    }

    public static Event of(String type) {
        return new Event(type);
    }

    public Event with(String key, String value) {
        json.addProperty(key, value);
        return this;
    }

    public Event with(String key, Number value) {
        json.addProperty(key, value);
        return this;
    }

    public Event with(String key, boolean value) {
        json.addProperty(key, value);
        return this;
    }

    public Event with(String key, UUID value) {
        json.addProperty(key, value == null ? null : value.toString());
        return this;
    }

    /** Adds any object (a record, list…) turned into JSON. */
    public Event withObject(String key, Object value) {
        JsonElement element = GSON.toJsonTree(value);
        json.add(key, element);
        return this;
    }

    public String toJson() {
        return GSON.toJson(json);
    }
}
