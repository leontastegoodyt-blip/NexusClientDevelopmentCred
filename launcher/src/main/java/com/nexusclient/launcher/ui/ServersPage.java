package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.servers.ServerStore;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ServersPage – the Servers tab: Featured servers, Nexus Partner Servers, the player's Favourites and
 * Recent servers. Every server shows live players and ping, and has one-click Join.
 */
public class ServersPage extends VBox {

    private final ServerStore store;
    private final Consumer<ServerStore.Server> onJoin;
    private final VBox body = new VBox(12);
    private TabStrip tabs;
    private String currentTab = "Featured";

    /**
     * @param onJoin called when Join is clicked (starts the game straight into that server)
     */
    public ServersPage(ServerStore store, Consumer<ServerStore.Server> onJoin) {
        this.store = store;
        this.onJoin = onJoin;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.SERVERS.title());
        title.getStyleClass().add("page-title");
        Label description = new Label(Page.SERVERS.description());
        description.getStyleClass().add("page-description");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(event -> showTab(currentTab));
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(new VBox(4, title, description), spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        tabs = new TabStrip(Page.SERVERS.tabs(), this::showTab);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, tabs, scroll);
        showTab(currentTab);
    }

    /** Lets a Partner Server's manager choose which Nexus mods are switched off on their server. */
    private void editPartnerRules(ServerStore.Partner partner) {
        TextField mods = new TextField(String.join(", ", partner.blockedMods()));
        mods.setPromptText("e.g. minimap, freelook, fullbright   (or *blockable)");
        Label help = new Label("Mod ids, separated by commas. \"*blockable\" switches off every server-blockable mod "
                + "(freelook, fullbright, waypoints, minimap, autoGG). The full list of ids is in docs/server-rules.md.");
        help.getStyleClass().add("torcher-text");
        help.setWrapText(true);
        Button save = new Button("Save");
        save.getStyleClass().add("primary-button");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(e -> Modal.close());
        save.setOnAction(e -> {
            List<String> ids = new ArrayList<>();
            for (String id : mods.getText().split(",")) {
                if (!id.isBlank()) {
                    ids.add(id.trim());
                }
            }
            Background.run("partner-rules", () -> com.nexusclient.launcher.online.NexusOnline.get()
                    .put("/api/partners/" + partner.id() + "/rules", java.util.Map.of("blocked", ids)), ok -> {
                Modal.close();
                Toast.info("Rules saved – players get them the next time they start the game");
                store.loadPartners();
            }, error -> Toast.error(error.getMessage()));
        });
        Label title = new Label("Nexus rules for " + partner.address());
        title.getStyleClass().add("form-title");
        VBox box = new VBox(12, title, help, mods, new HBox(8, save, cancel));
        box.getStyleClass().add("modal-card");
        Modal.show(box, 520);
    }

    /** Shows one sub-tab (public so the developer tour can visit it). */
    public void showTab(String tab) {
        currentTab = tab;
        tabs.select(tab);
        List<Node> content = new ArrayList<>();
        switch (tab) {
            case "Nexus Partners" -> {
                if (store.partners().isEmpty()) {
                    content.add(note("No Nexus Partner Servers yet. Servers that partner with Nexus are "
                            + "featured here, and choose which Nexus mods are allowed on them."));
                }
                for (ServerStore.Server server : store.partners()) {
                    content.add(card(server));
                    ServerStore.Partner details = store.partnerDetails(server.address());
                    if (details != null && !details.blockedMods().isEmpty()) {
                        content.add(note("Nexus mods switched off on " + server.name() + ": "
                                + String.join(", ", details.blockedMods()).replace("*blockable", "all server-blockable mods")));
                    }
                    if (details != null && details.youManage()) {
                        Button rules = new Button("Edit Nexus rules for " + server.name());
                        rules.getStyleClass().add("secondary-button");
                        rules.setOnAction(e -> editPartnerRules(details));
                        content.add(rules);
                    }
                }
            }
            case "Favourites" -> {
                content.add(addServerForm());
                if (store.favourites().isEmpty()) {
                    content.add(note("No favourites yet. Press ☆ on any server, or add one above."));
                }
                store.favourites().forEach(server -> content.add(card(server)));
            }
            case "Recent" -> {
                if (store.recent().isEmpty()) {
                    content.add(note("Servers you join through Nexus will show up here."));
                }
                store.recent().forEach(server -> content.add(card(server)));
            }
            default -> store.featured().forEach(server -> content.add(card(server)));
        }
        body.getChildren().setAll(content);
    }

    private Node card(ServerStore.Server server) {
        return new ServerCard(server, store, onJoin);
    }

    /** A small form to add a server to Favourites by its address. */
    private Node addServerForm() {
        TextField name = new TextField();
        name.setPromptText("Name, e.g. My SMP");
        TextField address = new TextField();
        address.setPromptText("Address, e.g. play.example.com");
        HBox.setHgrow(address, Priority.ALWAYS);
        Button add = new Button("Add server");
        add.getStyleClass().add("primary-button");
        add.setOnAction(event -> {
            String host = address.getText().trim();
            if (host.isEmpty()) {
                Toast.error("Type the server's address first.");
                return;
            }
            String label = name.getText().trim().isEmpty() ? host : name.getText().trim();
            store.setFavourite(new ServerStore.Server(label, host, ""), true);
            Toast.info(label + " added to favourites.");
            showTab("Favourites");
        });
        HBox form = new HBox(10, name, address, add);
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("settings-card");
        return form;
    }

    private static Node note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("torcher-text");
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.getStyleClass().add("settings-card");
        return box;
    }
}
