package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.i18n.Lang;

import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.function.Consumer;

/**
 * TabStrip – a row of sub-tabs across the top of a page, e.g. "Mods | Resource Packs | Shaders".
 * The selected one gets a glowing outline (see ".tab-strip-button" in theme.css).
 *
 * Works just like NavRail: ToggleButtons in a ToggleGroup, so only one is selected at a time.
 * It's a FlowPane, so when the window is narrow the tabs wrap onto a second row instead of being
 * squashed into "Ge…" and "…".
 */
public class TabStrip extends FlowPane {

    private final ToggleGroup group = new ToggleGroup();

    /**
     * @param tabs     the tab names, in order. The first one starts selected.
     * @param onSelect called with the tab's name whenever the player clicks a tab
     */
    public TabStrip(List<String> tabs, Consumer<String> onSelect) {
        getStyleClass().add("tab-strip");

        // Keep one tab selected even if the player clicks the active tab again.
        group.selectedToggleProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null && oldTab != null) {
                oldTab.setSelected(true);
            }
        });

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
}
