package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.instance.Instance;
import javafx.scene.control.Label;

/**
 * InstanceBadge – the small square with an instance's first letter, e.g. [P] for "PvP".
 *
 * Each instance gets its own accent colour so they're easy to tell apart at a glance.
 * The colour is picked from the instance's name, so the same name always gets the same colour.
 */
public final class InstanceBadge {

    /** The accent colours badges can have: cyan, magenta, violet, amber, mint. */
    private static final String[] ACCENTS = {"#3AA8FF", "#FF2BD6", "#8B7BFF", "#FFB547", "#3DE0A0"};

    private InstanceBadge() {}

    /**
     * Makes a badge for an instance.
     *
     * @param size width and height in pixels
     */
    public static Label create(Instance instance, double size) {
        Label badge = new Label(instance.name().substring(0, 1).toUpperCase());
        badge.getStyleClass().add("instance-badge");
        badge.setMinSize(size, size);
        badge.setPrefSize(size, size);
        badge.setMaxSize(size, size);

        // Colour codes can have 2 extra digits for see-through-ness: "66" ≈ 40%, "14" ≈ 8%.
        String accent = accentFor(instance);
        badge.setStyle("-fx-text-fill: " + accent + ";"
                + "-fx-border-color: " + accent + "66;"
                + "-fx-background-color: " + accent + "14;"
                + "-fx-font-size: " + Math.round(size * 0.42) + "px;");
        return badge;
    }

    /** Picks an accent colour from the instance's name. */
    private static String accentFor(Instance instance) {
        // hashCode() turns the name into a number; floorMod keeps it in range 0..4.
        return ACCENTS[Math.floorMod(instance.name().hashCode(), ACCENTS.length)];
    }
}
