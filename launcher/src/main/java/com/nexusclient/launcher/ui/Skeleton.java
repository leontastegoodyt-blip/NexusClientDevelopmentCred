package com.nexusclient.launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Skeleton – grey "outline" shapes that show where content will go before it's built.
 *
 * Apps like YouTube and Discord show shapes like these while things load. Nexus uses them on
 * tabs that aren't built yet, so each page already shows its layout (a grid of cards, a list,
 * a settings form) instead of being blank.
 */
public final class Skeleton {

    private Skeleton() {}

    /** A rounded grey bar of a fixed size – stands in for a line of text, a button, an icon... */
    public static Region bar(double width, double height) {
        Region bar = new Region();
        bar.getStyleClass().add("skeleton");
        bar.setMinSize(width, height);
        bar.setPrefSize(width, height);
        bar.setMaxSize(width, height);
        return bar;
    }

    /** A grey block that stretches to the full width available – stands in for a picture. */
    public static Region block(double height) {
        Region block = new Region();
        block.getStyleClass().add("skeleton");
        block.setMinHeight(height);
        block.setPrefHeight(height);
        block.setMaxWidth(Double.MAX_VALUE);
        return block;
    }

    /** A card: picture on top, title and a line of text underneath. For grids (mods, skins...). */
    public static VBox card() {
        VBox card = new VBox(10, block(110), bar(140, 12), bar(96, 10));
        card.getStyleClass().add("skeleton-card");
        return card;
    }

    /** A list row: icon, two lines of text, something on the right. For lists (servers, friends...). */
    public static HBox listRow() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(14, bar(40, 40), new VBox(8, bar(190, 12), bar(120, 10)), spacer, bar(72, 26));
        row.getStyleClass().add("skeleton-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** A settings row: a label on the left, a switch or dropdown on the right. */
    public static HBox settingRow() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(new VBox(8, bar(170, 12), bar(260, 10)), spacer, bar(46, 24));
        row.getStyleClass().add("skeleton-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
