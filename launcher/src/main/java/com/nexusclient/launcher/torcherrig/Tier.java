package com.nexusclient.launcher.torcherrig;

/** How powerful a PC is, from TorcherRig's point of view (the project brief, TorcherRig → Tiers). */
public enum Tier {
    LOW("Low"),
    MID("Mid"),
    HIGH("High"),
    ULTRA("Ultra");

    private final String label;

    Tier(String label) {
        this.label = label;
    }

    /** The name shown to players, e.g. "High". */
    public String label() {
        return label;
    }

    /** The weaker of two tiers. */
    public static Tier min(Tier a, Tier b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
