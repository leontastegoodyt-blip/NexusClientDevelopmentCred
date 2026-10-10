package com.nexusclient.launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Supplier;

/**
 * FlipTile – a small stat card that flips over when clicked, like a playing card.
 *
 *   front:  [icon]  CAPTION        back:  CAPTION
 *                   12                    a few lines of detail
 *
 * The front shows one number; the back shows the details behind it (worked out when it's flipped,
 * so it's always up to date). Click again to flip back. Used for the stats on Home.
 */
public class FlipTile extends StackPane {

    private final Label value = new Label("—");
    private final VBox front;
    private final VBox back;
    private final VBox backLines = new VBox(3);
    private final Supplier<List<String>> details;

    /**
     * @param icon    the icon on the front (Icons.…)
     * @param caption the small name above the number, e.g. "PLAYTIME"
     * @param details the lines shown on the back
     */
    public FlipTile(String icon, String caption, Supplier<List<String>> details) {
        this.details = details;
        getStyleClass().add("stat-tile");

        StackPane iconBox = new StackPane(Icons.create(icon, 18));
        iconBox.getStyleClass().add("stat-icon");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");
        captionLabel.setMinWidth(Region.USE_PREF_SIZE);
        value.getStyleClass().add("stat-value");
        // A tiny "turn me over" mark in the corner.
        Node flipMark = Icons.create(Icons.FLIP, 11);
        flipMark.getStyleClass().add("flip-mark");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(captionLabel, spacer, flipMark);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox frontRow = new HBox(10, iconBox, value);
        frontRow.setAlignment(Pos.CENTER_LEFT);
        front = new VBox(6, top, frontRow);

        Label backTitle = new Label(caption);
        backTitle.getStyleClass().add("stat-caption");
        back = new VBox(5, backTitle, backLines);
        back.getStyleClass().add("stat-tile-back");
        back.setVisible(false);

        getChildren().addAll(front, back);
        setAlignment(Pos.TOP_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this, Priority.ALWAYS);
        Tooltip.install(this, new Tooltip("Click to flip"));
        setOnMouseClicked(event -> flip());
    }

    /** The big number on the front. */
    public Label value() {
        return value;
    }

    /** Turns the card over (front ↔ back). */
    public void flip() {
        boolean showingFront = front.isVisible();
        if (showingFront) {
            backLines.getChildren().clear();
            for (String line : details.get()) {
                Label label = new Label(line);
                label.getStyleClass().add("stat-detail");
                label.setWrapText(true);
                backLines.getChildren().add(label);
            }
            Animations.flipOver(this, front, back);
        } else {
            Animations.flipOver(this, back, front);
        }
    }
}
