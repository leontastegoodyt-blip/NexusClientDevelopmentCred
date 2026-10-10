package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.i18n.Lang;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * TabStrip – a row of sub-tabs across the top of a page, e.g. "Mods | Resource Packs | Shaders".
 *
 * The tabs themselves are just text. Behind the selected one sits a glowing "pill" that slides
 * (and stretches) over to the next tab when you click it – see ".tab-pill" in theme.css.
 *
 * ToggleButtons in a ToggleGroup, so only one is selected at a time. It's a FlowPane, so when the
 * window is narrow the tabs wrap onto a second row instead of being squashed into "Ge…".
 * The pill is an "unmanaged" child: the FlowPane ignores it when lining up the tabs, and we place
 * it ourselves after every layout.
 */
public class TabStrip extends FlowPane {

    private final ToggleGroup group = new ToggleGroup();
    private final Region pill = new Region();
    private Timeline pillMove;
    /** 0 → 1 while the pill travels from its old place to the new tab. */
    private final DoubleProperty travel = new SimpleDoubleProperty(1);
    private Bounds from;
    private ToggleButton target;

    /**
     * @param tabs     the tab names, in order. The first one starts selected.
     * @param onSelect called with the tab's name whenever the player clicks a tab
     */
    public TabStrip(List<String> tabs, Consumer<String> onSelect) {
        getStyleClass().add("tab-strip");
        pill.getStyleClass().add("tab-pill");
        pill.setManaged(false);
        pill.setMouseTransparent(true);
        getChildren().add(pill); // first child = drawn behind the tabs

        // Keep one tab selected even if the player clicks the active tab again.
        group.selectedToggleProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null && oldTab != null) {
                oldTab.setSelected(true);
            } else if (newTab instanceof ToggleButton tab) {
                slidePillTo(tab, oldTab != null);
            }
        });
        travel.addListener((o, a, b) -> placePill());

        for (String name : tabs) {
            // The tab's text in the player's language; "name" stays the English id used by the code.
            ToggleButton tab = new ToggleButton(Lang.t("tab." + name.replace(" ", ""), name));
            tab.getStyleClass().add("tab-strip-button");
            tab.setMinWidth(Region.USE_PREF_SIZE); // never shrink the tab's text into "…"
            tab.setToggleGroup(group);
            tab.setUserData(name);
            tab.setOnAction(event -> onSelect.accept(name));
            getChildren().add(tab);
        }
        if (!group.getToggles().isEmpty()) {
            group.getToggles().get(0).setSelected(true);
        }
    }

    /** Highlights a tab when it's opened by the program rather than by a click. */
    public void select(String name) {
        for (Toggle toggle : group.getToggles()) {
            if (name.equals(toggle.getUserData())) {
                toggle.setSelected(true);
            }
        }
    }

    // ------------------------------------------------------------------ the sliding pill

    private void slidePillTo(ToggleButton tab, boolean animate) {
        from = target != null && pill.getWidth() > 0 ? pill.getBoundsInParent() : null;
        target = tab;
        if (pillMove != null) {
            pillMove.stop();
        }
        if (!animate || from == null || !Animations.enabled) {
            travel.set(1);
            placePill();
            return;
        }
        travel.set(0);
        pillMove = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(travel, 1, Animations.SPRING)));
        pillMove.play();
    }

    /** Puts the pill between where it came from and the selected tab ("travel" of the way there). */
    private void placePill() {
        if (target == null || target.getWidth() == 0) {
            return;
        }
        double t = travel.get();
        double x = target.getLayoutX();
        double y = target.getLayoutY();
        double w = target.getWidth();
        double h = target.getHeight();
        if (from != null && t < 1) {
            x = from.getMinX() + (x - from.getMinX()) * t;
            y = from.getMinY() + (y - from.getMinY()) * t;
            w = from.getWidth() + (w - from.getWidth()) * t;
            h = from.getHeight() + (h - from.getHeight()) * t;
        }
        pill.resizeRelocate(x, y, Math.max(0, w), Math.max(0, h));
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        placePill(); // the tabs may have moved (window resized, tabs wrapped onto a new row)
    }
}
