package com.nexusclient.core.module;

/** The groups the mod menu sorts modules into (the project brief, section 4). */
public enum Category {
    HUD("HUD"),
    PVP("PvP"),
    VISUAL("Visual"),
    CHAT("Chat"),
    UTILITY("Utility");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
