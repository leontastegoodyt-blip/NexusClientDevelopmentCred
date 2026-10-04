package com.nexusclient.launcher.ui;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * NavRail – the thin bar of tab icons on the far left of the window.
 *
 * Each icon is a ToggleButton (a button that stays pressed in). They're all in one ToggleGroup,
 * which makes sure only one tab is selected at a time. Settings sits at the bottom, like in
 * most launchers.
 */
public class NavRail extends VBox {

    /** Each page's tab, so a tab can be lit up when its page is opened some other way. */
    private final Map<Page, ToggleButton> tabs = new EnumMap<>(Page.class);

    /**
     * @param onSelect called with the chosen page whenever the player clicks a tab
     */
    public NavRail(Consumer<Page> onSelect) {
        getStyleClass().add("nav-rail");

        ToggleGroup group = new ToggleGroup();

        // Clicking the tab that's already selected would normally un-select it, leaving no tab
        // selected. This puts the selection back so one tab is always active.
        group.selectedToggleProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null && oldTab != null) {
                oldTab.setSelected(true);
            }
        });

        // A stretchy empty space that pushes Settings to the bottom.
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        for (Page page : Page.values()) {
            ToggleButton tab = new ToggleButton(page.shortLabel());
            tab.setGraphic(Icons.create(page.icon(), 20));
            tab.setContentDisplay(ContentDisplay.TOP);  // icon on top, small label underneath
            tab.setTooltip(new Tooltip(page.title()));  // full name shows when you hover
            tab.getStyleClass().add("nav-button");
            tab.setToggleGroup(group);
            tab.setOnAction(event -> onSelect.accept(page));
            tabs.put(page, tab);

            if (page == Page.SETTINGS) {
                getChildren().addAll(spacer, tab);
            } else {
                getChildren().add(tab);
            }
            if (page == Page.HOME) {
                tab.setSelected(true); // start on Home
            }
        }
    }

    /** Lights up the tab for this page (without opening the page again). */
    public void select(Page page) {
        ToggleButton tab = tabs.get(page);
        if (tab != null && !tab.isSelected()) {
            tab.setSelected(true);
        }
    }
}
