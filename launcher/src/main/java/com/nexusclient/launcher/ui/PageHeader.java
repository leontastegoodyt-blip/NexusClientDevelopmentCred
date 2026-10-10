package com.nexusclient.launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * PageHeader – the title block at the top of every tab page:
 *
 *     ⬡ NEXUS › MODS          ← a small "kicker" line with the logo, in the accent colour
 *     Mods                    ← the big title
 *
 * No paragraph of explanation under it (the owner asked for less text) – the page's description
 * is still there as a tooltip when you hover the title.
 */
public final class PageHeader {

    private PageHeader() {}

    /**
     * @param page  which page this is (for the kicker and the tooltip)
     * @param title the page's title label (each page makes its own, styled "page-title")
     * @param extra anything else to show under the title (e.g. Friends' online status)
     */
    public static VBox block(Page page, Label title, Node... extra) {
        NexusLogo logo = new NexusLogo(14, NexusLogo.Style.BLUE, false);
        Label path = new Label("NEXUS  ›  " + page.title().toUpperCase());
        path.getStyleClass().add("page-kicker");
        HBox kicker = new HBox(6, logo, path);
        kicker.setAlignment(Pos.CENTER_LEFT);

        title.setTooltip(new Tooltip(page.description()));
        VBox block = new VBox(2, kicker, title);
        block.getChildren().addAll(extra);
        block.getStyleClass().add("page-header-block");
        return block;
    }
}
