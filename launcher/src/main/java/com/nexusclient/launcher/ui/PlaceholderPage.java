package com.nexusclient.launcher.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * PlaceholderPage – shown for tabs that haven't been built yet.
 *
 * It already has the real page's frame: a title, a description, the row of sub-tabs, and a
 * greyed-out outline (a "skeleton") of what the page will look like, gently pulsing.
 * When a tab gets built in its step, it gets its own real page class instead of this.
 */
public class PlaceholderPage extends VBox {

    private final Page page;
    private final VBox body = new VBox(16);
    private FadeTransition pulse; // the gentle fade in/out on the skeleton

    public PlaceholderPage(Page page) {
        this.page = page;
        getStyleClass().add("tab-page");

        // --- Header: title + description on the left, "coming in step X" on the right ---
        Label title = new Label(page.title());
        title.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label step = new Label("Coming in build step " + page.buildStep());
        step.getStyleClass().add("step-pill");

        HBox header = new HBox(PageHeader.block(page, title), spacer, step);
        header.setAlignment(Pos.CENTER_LEFT);

        // --- Sub-tabs, and the area underneath that changes with them ---
        TabStrip tabs = new TabStrip(page.tabs(), this::showTab);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true); // content stretches to the full width
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, tabs, scroll);
        showTab(page.tabs().get(0));
    }

    /** Shows the skeleton for one sub-tab. */
    private void showTab(String tab) {
        Label note = new Label("Preview of \"" + tab + "\" – this arrives in build step " + page.buildStep() + ".");
        note.getStyleClass().add("preview-note");

        Node preview = switch (page) {
            case MODS, SKINS, TOOLS -> cardGrid();    // things you browse as pictures
            case SETTINGS -> settingsForm();           // switches and dropdowns
            default -> rowList();                      // servers, friends, clans, errors: lists
        };

        body.getChildren().setAll(note, preview);
        startPulse(preview);
    }

    /** A search bar, some filter buttons and a grid of cards. */
    private Node cardGrid() {
        HBox toolbar = new HBox(10, Skeleton.bar(280, 36), Skeleton.bar(90, 36), Skeleton.bar(90, 36));
        TilePane grid = new TilePane(16, 16);
        grid.setPrefTileWidth(210);
        grid.setMaxWidth(Double.MAX_VALUE); // use the full width, so as many cards fit per row as possible
        for (int i = 0; i < 8; i++) {
            grid.getChildren().add(Skeleton.card());
        }
        return new VBox(16, toolbar, grid);
    }

    /** A search bar and a list of rows. */
    private Node rowList() {
        VBox list = new VBox(10, Skeleton.bar(280, 36));
        for (int i = 0; i < 6; i++) {
            list.getChildren().add(Skeleton.listRow());
        }
        return list;
    }

    /** Two groups of settings rows, each under a heading. */
    private Node settingsForm() {
        VBox form = new VBox(10, Skeleton.bar(120, 14));
        for (int i = 0; i < 3; i++) {
            form.getChildren().add(Skeleton.settingRow());
        }
        form.getChildren().add(Skeleton.bar(120, 14));
        for (int i = 0; i < 3; i++) {
            form.getChildren().add(Skeleton.settingRow());
        }
        return form;
    }

    /** Makes the skeleton slowly fade between full and half visible, like it's loading. */
    private void startPulse(Node node) {
        if (pulse != null) {
            pulse.stop(); // stop the old tab's animation first
        }
        pulse = new FadeTransition(Duration.seconds(1.4), node);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.45);
        pulse.setAutoReverse(true);                  // fade out, then back in
        pulse.setCycleCount(Animation.INDEFINITE);   // forever
        pulse.play();
    }
}
