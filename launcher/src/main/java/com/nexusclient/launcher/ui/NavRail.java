package com.nexusclient.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * NavRail – the slim bar of tab icons on the far left of the window.
 *
 * Simple on purpose: just icons. The details come from movement:
 *   - a glowing "indicator" (a soft square behind the icon + a gradient bar on the left edge)
 *     slides smoothly to whichever tab is selected
 *   - hovering an icon makes its name flip out to the right of the bar
 *
 * Each icon is a ToggleButton in one ToggleGroup, so only one tab is selected at a time.
 * Settings sits at the bottom, like in most launchers.
 *
 * The indicator and the flip-out label are "unmanaged" children: the VBox ignores them when it lines
 * up the buttons, and we place them ourselves. The rail is drawn on top of the Instances panel
 * (setViewOrder) so the flip-out label can stick out over it.
 */
public class NavRail extends VBox {

    /** Each page's tab, so a tab can be lit up when its page is opened some other way. */
    private final Map<Page, ToggleButton> tabs = new EnumMap<>(Page.class);

    private final Region indicator = new Region();     // the soft glowing square behind the icon
    private final Region indicatorBar = new Region();  // the gradient bar on the rail's left edge
    private final Label flyout = new Label();          // the name that flips out on hover
    private Timeline indicatorMove;
    private ToggleButton selected;

    /**
     * @param onSelect called with the chosen page whenever the player clicks a tab
     */
    public NavRail(Consumer<Page> onSelect) {
        getStyleClass().add("nav-rail");
        setViewOrder(-1); // draw above the Instances panel, so the flip-out label isn't hidden under it

        for (Region part : new Region[] {indicator, indicatorBar}) {
            part.setManaged(false);
            part.setMouseTransparent(true);
        }
        indicator.getStyleClass().add("nav-indicator");
        indicatorBar.getStyleClass().add("nav-indicator-bar");
        flyout.getStyleClass().add("nav-flyout");
        flyout.setManaged(false);
        flyout.setMouseTransparent(true);
        flyout.setVisible(false);
        getChildren().addAll(indicator, indicatorBar);

        ToggleGroup group = new ToggleGroup();
        // Clicking the tab that's already selected would normally un-select it, leaving no tab
        // selected. This puts the selection back so one tab is always active.
        group.selectedToggleProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null && oldTab != null) {
                oldTab.setSelected(true);
            } else if (newTab instanceof ToggleButton tab) {
                moveIndicatorTo(tab, oldTab != null);
            }
        });

        // A stretchy empty space that pushes Settings to the bottom.
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        for (Page page : Page.values()) {
            ToggleButton tab = new ToggleButton();
            tab.setGraphic(Icons.create(page.icon(), 21));
            tab.getStyleClass().add("nav-button");
            tab.setAccessibleText(page.title()); // screen readers still hear the tab's name
            tab.setToggleGroup(group);
            tab.setOnAction(event -> onSelect.accept(page));
            tab.hoverProperty().addListener((o, wasHover, isHover) -> {
                if (isHover) {
                    showFlyout(tab, page.title());
                } else {
                    hideFlyout();
                }
            });
            tabs.put(page, tab);

            if (page == Page.DEVELOPER) {
                // Hidden until the Nexus server confirms this is the developer's account.
                tab.setVisible(false);
                tab.setManaged(false);
                getChildren().addAll(spacer, tab);
            } else if (page == Page.SETTINGS) {
                getChildren().add(tab);
            } else {
                getChildren().add(tab);
            }
        }
        getChildren().add(flyout); // last, so it's drawn on top of everything in the rail

        // Keep the indicator on the selected tab when the window changes size (Settings moves).
        heightProperty().addListener((o, a, b) -> Platform.runLater(() -> {
            if (selected != null) {
                moveIndicatorTo(selected, false);
            }
        }));
        tabs.get(Page.HOME).setSelected(true); // start on Home
    }

    /** Shows or hides the Developer tab (only the owner's account gets it). */
    public void showDeveloperTab(boolean show) {
        ToggleButton tab = tabs.get(Page.DEVELOPER);
        tab.setVisible(show);
        tab.setManaged(show);
        if (selected != null) {
            Platform.runLater(() -> moveIndicatorTo(selected, false)); // Settings may have moved down
        }
    }

    /** Lights up the tab for this page (without opening the page again). */
    public void select(Page page) {
        ToggleButton tab = tabs.get(page);
        if (tab != null && !tab.isSelected()) {
            tab.setSelected(true);
        }
    }

    // ------------------------------------------------------------------ the sliding indicator

    /** Slides the glowing indicator to a tab (or jumps there, when the window first opens). */
    private void moveIndicatorTo(ToggleButton tab, boolean animate) {
        selected = tab;
        // The buttons may not have been laid out yet (first time) – wait one pulse if so.
        if (tab.getHeight() == 0) {
            Platform.runLater(() -> moveIndicatorTo(tab, false));
            return;
        }
        double size = 44;
        double x = (getWidth() - size) / 2;
        double y = tab.getLayoutY() + (tab.getHeight() - size) / 2;
        indicator.resize(size, size);
        indicator.setLayoutX(x);
        indicatorBar.resize(3, 24);
        indicatorBar.setLayoutX(0);

        if (indicatorMove != null) {
            indicatorMove.stop();
        }
        double barY = tab.getLayoutY() + (tab.getHeight() - 24) / 2;
        if (!animate || !Animations.enabled) {
            indicator.setLayoutY(y);
            indicatorBar.setLayoutY(barY);
            return;
        }
        // The square glides with a springy finish; the bar stretches as it travels.
        indicatorMove = new Timeline(
                new KeyFrame(Duration.millis(320),
                        new KeyValue(indicator.layoutYProperty(), y, Animations.SPRING),
                        new KeyValue(indicatorBar.layoutYProperty(), barY, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(140), new KeyValue(indicatorBar.scaleYProperty(), 1.8)),
                new KeyFrame(Duration.millis(320), new KeyValue(indicatorBar.scaleYProperty(), 1.0)));
        indicatorMove.play();
    }

    // ------------------------------------------------------------------ the flip-out label

    private void showFlyout(ToggleButton tab, String name) {
        flyout.setText(name);
        flyout.applyCss();
        double width = flyout.prefWidth(-1);
        double height = flyout.prefHeight(-1);
        flyout.resize(width, height);
        flyout.setLayoutX(getWidth() + 6);
        flyout.setLayoutY(tab.getLayoutY() + (tab.getHeight() - height) / 2);
        flyout.setVisible(true);
        if (!Animations.enabled) {
            return;
        }
        // Flips out from the rail: starts thin and to the left, springs to full size.
        flyout.setScaleX(0.4);
        flyout.setOpacity(0);
        flyout.setTranslateX(-(width * 0.3));
        ScaleTransition grow = new ScaleTransition(Duration.millis(240), flyout);
        grow.setToX(1);
        grow.setInterpolator(Animations.SPRING);
        TranslateTransition slide = new TranslateTransition(Duration.millis(240), flyout);
        slide.setToX(0);
        slide.setInterpolator(Animations.SPRING);
        FadeTransition fade = new FadeTransition(Duration.millis(140), flyout);
        fade.setToValue(1);
        new ParallelTransition(grow, slide, fade).play();
    }

    private void hideFlyout() {
        flyout.setVisible(false);
    }

}
