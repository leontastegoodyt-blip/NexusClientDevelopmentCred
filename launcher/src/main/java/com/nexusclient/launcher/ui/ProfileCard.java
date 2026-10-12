package com.nexusclient.launcher.ui;

import com.google.gson.JsonObject;
import com.nexusclient.launcher.online.NexusOnline;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.function.Consumer;

/**
 * ProfileCard – a pop-up about any Nexus player (the project brief: "Profile cards: skin, playtime and favourite
 * server"): their skin in 3D, online status and server (friends only, and only if they share it),
 * playtime, favourite server and when they were last seen. Buttons: add friend, invite to party,
 * render their workspace (Mod Rerender), report.
 *
 * Open one with {@link #show(String, String)} – player rows (People.row) do it when clicked.
 */
public final class ProfileCard {

    /** What "Render workspace" does (set by NexusLauncher: open the Mod Rerender tab on this name). */
    public static Consumer<String> onRenderWorkspace = name -> {};

    private ProfileCard() {}

    /** Opens the card for a player (name or UUID); the details load in the background. */
    public static void show(String uuid, String name) {
        NexusOnline online = NexusOnline.get();
        SkinModel3D skin = new SkinModel3D(190, 260);
        skin.setSkin(DefaultSkin.create(), false);
        Label title = new Label(name);
        title.getStyleClass().addAll("section-title", "private");
        Label status = new Label("Loading…");
        status.getStyleClass().add("mod-meta");
        VBox details = new VBox(8, title, status);
        details.setMinWidth(300);
        HBox top = new HBox(18, skin, details);
        top.setAlignment(Pos.CENTER_LEFT);
        Modal.show(top, 620);

        // The 3D skin.
        if (uuid != null && !uuid.isEmpty()) {
            Background.run("profile-skin", () -> SkinHeads.skinFor(uuid), image -> {
                if (image != null) {
                    skin.setSkin(image, false);
                }
            }, null);
        }
        if (!online.signedIn()) {
            status.setText("Sign in to Nexus to see profile cards.");
            return;
        }
        String who = uuid != null && !uuid.isEmpty() ? uuid : name;
        Background.run("profile-card", () -> online.get("/api/players/" + who).getAsJsonObject(), card -> {
            fill(details, title, status, card);
        }, error -> status.setText(error.getMessage()));
    }

    private static void fill(VBox details, Label title, Label status, JsonObject card) {
        String name = str(card, "name");
        String uuid = str(card, "uuid");
        title.setText(name);
        boolean friend = card.get("friend").getAsBoolean();
        boolean online = card.get("online").getAsBoolean();
        String server = str(card, "server");
        status.setText(friend ? (online ? (server.isEmpty() ? "● Online" : "● Playing on " + server) : "Offline")
                : "Not your friend yet");
        status.getStyleClass().setAll(online ? "person-detail-online" : "mod-meta");

        long minutes = card.get("playtimeMinutes").getAsLong();
        String favourite = str(card, "favouriteServer");
        String lastSeen = str(card, "lastSeen");
        HBox chips = People.statChips(
                (minutes >= 60 ? minutes / 60 + " h " : "") + minutes % 60 + " min played",
                favourite.isEmpty() ? "No favourite server" : "★ " + favourite,
                lastSeen.isEmpty() ? "Never seen" : "Seen " + lastSeen.substring(0, Math.min(10, lastSeen.length())));

        // --- Buttons ---
        NexusOnline nexus = NexusOnline.get();
        FlowPane buttons = new FlowPane(8, 8);
        if (!friend && !uuid.equals(nexus.myUuid())) {
            buttons.getChildren().add(button("Add friend", true, () -> call(
                    () -> nexus.post("/api/friends/request", Map.of("name", name)), "Friend request sent to " + name + ".")));
        }
        if (friend && online) {
            buttons.getChildren().add(button("Invite to party", true, () -> call(
                    () -> nexus.post("/api/party/invite", Map.of("uuid", uuid)), "Invited " + name + " to your party.")));
        }
        buttons.getChildren().add(button("Render workspace", false, () -> {
            Modal.close();
            onRenderWorkspace.accept(name);
        }));
        if (!uuid.equals(nexus.myUuid())) {
            buttons.getChildren().add(button("Report", false, () -> report(uuid, name)));
        }
        details.getChildren().setAll(title, status, chips, buttons);
    }

    /** A short report form: a reason and a few words. */
    private static void report(String uuid, String name) {
        Label heading = new Label("Report " + name);
        heading.getStyleClass().add("torcher-title");
        ComboBox<String> reason = new ComboBox<>();
        reason.getItems().setAll("CHAT", "NAME", "SKIN", "CHEATING", "OTHER");
        reason.setValue("CHAT");
        TextField details = new TextField();
        details.setPromptText("What happened? (optional)");
        Button send = button("Send report", true, () -> call(() -> NexusOnline.get().post("/api/reports",
                        Map.of("target", uuid, "reason", reason.getValue(), "details", details.getText().trim())),
                "Thanks – Nexus staff will look at it."));
        Button cancel = button("Cancel", false, Modal::close);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Modal.show(new VBox(12, heading, reason, details, new HBox(10, send, spacer, cancel)), 440);
    }

    private interface Call {
        Object run() throws Exception;
    }

    private static void call(Call action, String done) {
        Background.run("profile-action", action::run, result -> {
            Modal.close();
            Toast.info(done);
        }, error -> Toast.error(error.getMessage()));
    }

    private static Button button(String text, boolean primary, Runnable onClick) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setOnAction(event -> onClick.run());
        return button;
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
