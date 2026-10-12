package com.nexusclient.launcher.ui;

import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * People – shared pieces for the pages that list players (Friends, Clans, Developer):
 *
 *   [head●]  Name  [badge]                         [▶] [💬] [⋯]
 *            Playing on hypixel.net
 *
 *   - row(): a player card with their skin head and an online dot
 *   - iconButton(): a small square button with just an icon (its name shows on hover)
 *   - moreMenu(): a "⋯" button that opens a menu, for actions you don't need every day
 *   - empty(): a short "nothing here" message with an icon
 *
 * Keeping these in one place means every list of players looks and behaves the same.
 */
public final class People {

    private People() {}

    /**
     * One player card.
     *
     * @param uuid    their Minecraft UUID (for the skin head), or null for a letter instead
     * @param online  true = green dot, false = grey dot, null = no dot
     * @param badge   a small tag after the name (e.g. "LEADER", "DEV"), or null
     * @param actions buttons on the right
     */
    public static HBox row(String uuid, String name, String detail, Boolean online, String badge, List<Node> actions) {
        Node head;
        if (uuid != null) {
            head = SkinHeads.forUuid(uuid, 38);
        } else {
            Label letter = new Label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            letter.getStyleClass().add("instance-badge");
            letter.setMinSize(38, 38);
            letter.setAlignment(Pos.CENTER);
            head = letter;
        }
        StackPane headBox = new StackPane(head);
        if (online != null) {
            Region dot = new Region();
            dot.getStyleClass().addAll("status-dot", online ? "status-dot-on" : "status-dot-off");
            StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
            headBox.getChildren().add(dot);
        }

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().addAll("mod-title", "private"); // "private" = hidden in streamer mode
        HBox nameLine = new HBox(8, nameLabel);
        nameLine.setAlignment(Pos.CENTER_LEFT);
        if (badge != null) {
            Label tag = new Label(badge);
            tag.getStyleClass().add("person-badge");
            nameLine.getChildren().add(tag);
        }
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().addAll(Boolean.TRUE.equals(online) ? "person-detail-online" : "mod-meta", "private");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, headBox, new VBox(2, nameLine, detailLabel), spacer);
        HBox buttons = new HBox(6);
        buttons.getChildren().addAll(actions);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().add(buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("mod-row", "person-card");
        return row;
    }

    /** A small square button with only an icon. Its name shows as a tooltip on hover. */
    public static Button iconButton(String icon, String name, boolean primary, Runnable action) {
        Button button = new Button(null, Icons.create(icon, 16));
        button.getStyleClass().addAll("icon-button", primary ? "icon-button-primary" : "icon-button-plain");
        button.setTooltip(new Tooltip(name));
        button.setAccessibleText(name);
        button.setOnAction(e -> action.run());
        return button;
    }

    /** A "⋯" button that opens a small menu (name → what happens). Names starting with "!" are red. */
    public static Button moreMenu(Map<String, Runnable> actions) {
        Button more = new Button("⋯");
        more.getStyleClass().addAll("icon-button", "more-button");
        more.setTooltip(new Tooltip("More"));
        ContextMenu menu = new ContextMenu();
        actions.forEach((name, action) -> {
            boolean danger = name.startsWith("!");
            MenuItem item = new MenuItem(danger ? name.substring(1) : name);
            if (danger) {
                item.getStyleClass().add("menu-danger");
            }
            item.setOnAction(e -> action.run());
            menu.getItems().add(item);
        });
        more.setOnAction(e -> menu.show(more, Side.BOTTOM, 0, 4));
        return more;
    }

    /** Shortcut for building an ordered menu: People.menu("Remove", r1, "!Block", r2). */
    public static Map<String, Runnable> menu(Object... nameThenAction) {
        Map<String, Runnable> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < nameThenAction.length; i += 2) {
            map.put((String) nameThenAction[i], (Runnable) nameThenAction[i + 1]);
        }
        return map;
    }

    /** A short "nothing here" card: a glowing icon, a title, and one short line. */
    public static VBox empty(String icon, String title, String text) {
        Node iconNode = Icons.create(icon, 30);
        iconNode.getStyleClass().add("empty-icon");
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        VBox box = new VBox(6, iconNode, t);
        if (text != null) {
            Label d = new Label(text);
            d.getStyleClass().add("empty-text");
            d.setWrapText(true);
            box.getChildren().add(d);
        }
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().addAll("settings-card", "empty-card");
        return box;
    }

    /** A row of small stat chips, e.g. "● 2 online   5 friends   1 request". */
    public static HBox statChips(String... texts) {
        HBox chips = new HBox(8);
        for (String text : texts) {
            Label chip = new Label(text);
            chip.getStyleClass().add("status-chip");
            chips.getChildren().add(chip);
        }
        return chips;
    }
}
