package com.nexusclient.launcher.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.online.NexusOnline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeveloperPage – the owner's control room. Only the developer's account sees this tab (NavRail), and
 * the Nexus server double-checks the role on EVERY action, so a modified launcher can't use it.
 *
 *   Overview  – live numbers: players, online now, open reports, banned
 *   Players   – search anyone who has used Nexus: kick, ban for a time, mute, give Premium
 *   Reports   – open player reports: close them or ban straight away
 *   Events    – start honor events (honor × multiplier for a while)
 *   Partners  – add / remove Nexus Partner Servers
 *   Crashes   – crash reports players chose to send
 */
public class DeveloperPage extends VBox {

    private final NexusOnline online = NexusOnline.get();
    private final VBox body = new VBox(10);
    private String currentTab = "Overview";

    public DeveloperPage() {
        getStyleClass().add("tab-page");
        Label title = new Label(Page.DEVELOPER.title());
        title.getStyleClass().add("page-title");
        Label role = new Label("DEVELOPER");
        role.getStyleClass().addAll("person-badge", "dev-badge");
        Button refresh = button("Refresh", "secondary-button", this::show);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, PageHeader.block(Page.DEVELOPER, title, role), spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        TabStrip tabs = new TabStrip(Page.DEVELOPER.tabs(), tab -> {
            currentTab = tab;
            show();
        });
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, tabs, scroll);
        show();
    }

    private void show() {
        body.getChildren().setAll(People.empty(Icons.CODE, "Loading…", null));
        switch (currentTab) {
            case "Overview" -> showOverview();
            case "Players" -> showPlayers();
            case "Reports" -> call(() -> online.get("/api/admin/reports"), r -> showReports(r.getAsJsonArray()));
            case "Events" -> call(() -> online.get("/api/events"), r -> showEvents(r.getAsJsonArray()));
            case "Partners" -> call(() -> online.get("/api/partners"), r -> showPartners(r.getAsJsonArray()));
            case "Crashes" -> call(() -> online.get("/api/admin/crash-reports"), r -> showCrashes(r.getAsJsonArray()));
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Overview

    private void showOverview() {
        call(() -> online.get("/api/admin/overview"), result -> {
            JsonObject o = result.getAsJsonObject();
            HBox tiles = new HBox(12,
                    bigStat(Icons.FRIENDS, "PLAYERS", o.get("players").getAsLong()),
                    bigStat(Icons.PLAY, "ONLINE NOW", o.get("onlineNow").getAsLong()),
                    bigStat(Icons.ERROR_SCAN, "OPEN REPORTS", o.get("openReports").getAsLong()),
                    bigStat(Icons.BAN, "BANNED", o.get("banned").getAsLong()));
            Label quick = new Label("Quick actions");
            quick.getStyleClass().add("section-title");
            HBox actions = new HBox(8,
                    button("Find a player", "primary-button", () -> jump("Players")),
                    button("Start an honor event", "secondary-button", () -> jump("Events")),
                    button("Open Nexus folder", "secondary-button", () -> openFolder(NexusPaths.root())),
                    button("Server log", "secondary-button", () -> openFolder(NexusPaths.root().resolve("server"))));
            Label server = new Label("Server: " + online.baseUrl() + "  ·  signed in as " + online.myName());
            server.getStyleClass().add("mod-meta");
            body.getChildren().setAll(tiles, quick, actions, server);
            Animations.flipInCards(body);
        });
    }

    private Node bigStat(String icon, String caption, long value) {
        FlipTile tile = new FlipTile(icon, caption, () -> List.of(caption.charAt(0)
                + caption.substring(1).toLowerCase() + ": " + value, "Updated just now"));
        Animations.countUp(tile.value(), value, "");
        return tile;
    }

    private TabStrip findTabs() {
        return (TabStrip) getChildren().get(1);
    }

    private void jump(String tab) {
        findTabs().select(tab);
        currentTab = tab;
        show();
    }

    // ------------------------------------------------------------------ Players

    private void showPlayers() {
        TextField search = new TextField();
        search.setPromptText("Search players by name (empty = most recent)");
        HBox.setHgrow(search, Priority.ALWAYS);
        VBox results = new VBox(8);
        Runnable run = () -> call(() -> online.get("/api/admin/players?search="
                + URLEncoder.encode(search.getText().trim(), StandardCharsets.UTF_8)), r -> {
            results.getChildren().clear();
            JsonArray list = r.getAsJsonArray();
            if (list.isEmpty()) {
                results.getChildren().add(People.empty(Icons.SEARCH, "No players found", null));
            }
            for (JsonElement e : list) {
                results.getChildren().add(playerRow(e.getAsJsonObject(), () -> search.getOnAction().handle(null)));
            }
            Animations.flipInCards(results);
        });
        search.setOnAction(e -> run.run());
        body.getChildren().setAll(new HBox(8, search, People.iconButton(Icons.SEARCH, "Search", true, run)), results);
        run.run();
    }

    /** One player with everything a developer might do to them. */
    private Node playerRow(JsonObject p, Runnable refresh) {
        String uuid = p.get("uuid").getAsString();
        String name = p.get("name").getAsString();
        String role = p.get("role").getAsString();
        boolean isOnline = p.get("online").getAsBoolean();
        boolean banned = !p.get("bannedUntil").isJsonNull();
        boolean muted = !p.get("mutedUntil").isJsonNull();
        boolean premium = p.get("premium").getAsBoolean();
        boolean me = uuid.equals(online.myUuid());

        String server = p.get("server").isJsonNull() ? "" : p.get("server").getAsString();
        String detail = banned ? "Banned until " + shortTime(p.get("bannedUntil").getAsString())
                + (p.get("banReason").isJsonNull() ? "" : " · " + p.get("banReason").getAsString())
                : isOnline ? (server.isEmpty() ? "Online" : "Online on " + server)
                : "Last seen " + shortTime(p.get("lastSeen").getAsString());
        detail += "  ·  " + p.get("playtimeMinutes").getAsLong() / 60 + " h played";

        String badge = !role.equals("PLAYER") ? role : banned ? "BANNED" : muted ? "MUTED" : premium ? "PREMIUM" : null;
        List<Node> buttons = new ArrayList<>();
        if (!me && !role.equals("DEVELOPER")) {
            buttons.add(People.iconButton(Icons.KICK, "Kick from Nexus", false, () -> askReason("Kick " + name + "?",
                    "They're signed out of Nexus right now (they can sign in again).", "Kick", reason ->
                            call(() -> online.post("/api/admin/players/" + uuid + "/kick", Map.of("reason", reason)), r -> {
                                Toast.info(name + " was kicked");
                                refresh.run();
                            }))));
            buttons.add(People.iconButton(Icons.BAN, banned ? "Change ban" : "Ban for a time", false,
                    () -> banDialog(uuid, name, refresh)));
            Map<String, Runnable> more = new LinkedHashMap<>();
            if (banned) {
                more.put("Unban", () -> call(() -> online.post("/api/admin/players/" + uuid + "/ban",
                        Map.of("hours", 0, "reason", "")), r -> refresh.run()));
            }
            more.put(muted ? "Unmute" : "Mute for 24 hours", () -> call(() -> online.post(
                    "/api/admin/players/" + uuid + "/mute", Map.of("hours", muted ? 0 : 24)), r -> refresh.run()));
            more.put(premium ? "Remove Premium" : "Give Premium (30 days)", () -> call(() -> online.post(
                    "/api/admin/players/" + uuid + "/premium", Map.of("days", premium ? 0 : 30)), r -> refresh.run()));
            more.put("Copy UUID", () -> copy(uuid));
            buttons.add(People.moreMenu(more));
        }
        HBox row = People.row(uuid, name + (me ? " (you)" : ""), detail, isOnline, badge, buttons);
        if (banned) {
            row.getStyleClass().add("banned-row");
        }
        return row;
    }

    /** Ban with a chosen length and reason. */
    private void banDialog(String uuid, String name, Runnable refresh) {
        Label title = new Label("Ban " + name);
        title.getStyleClass().add("section-title");
        ComboBox<String> length = new ComboBox<>();
        Map<String, Integer> hours = new LinkedHashMap<>();
        hours.put("1 hour", 1);
        hours.put("1 day", 24);
        hours.put("3 days", 72);
        hours.put("1 week", 168);
        hours.put("30 days", 720);
        hours.put("1 year", 8760);
        hours.put("Permanent (10 years)", 87600);
        length.getItems().setAll(hours.keySet());
        length.setValue("1 day");
        length.getStyleClass().add("form-combo");
        TextField reason = new TextField();
        reason.setPromptText("Reason (they'll see it)");
        Label note = new Label("They're signed out of Nexus straight away and can't use friends, clans or "
                + "cosmetics until the ban ends.");
        note.getStyleClass().add("mod-meta");
        note.setWrapText(true);
        Button ban = button("Ban", "danger-button", () -> call(() -> online.post("/api/admin/players/" + uuid + "/ban",
                Map.of("hours", hours.get(length.getValue()), "reason", reason.getText().trim())), r -> {
            Modal.close();
            Toast.info(name + " is banned for " + length.getValue());
            refresh.run();
        }));
        VBox box = new VBox(12, title, new HBox(8, length, reason), note,
                new HBox(8, ban, button("Cancel", "secondary-button", Modal::close)));
        HBox.setHgrow(reason, Priority.ALWAYS);
        box.getStyleClass().add("modal-card");
        Modal.show(box, 520);
    }

    /** A small pop-up asking for a reason before doing something. */
    private void askReason(String titleText, String message, String confirm, Consumer<String> action) {
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        Label text = new Label(message);
        text.getStyleClass().add("mod-meta");
        text.setWrapText(true);
        TextField reason = new TextField();
        reason.setPromptText("Reason (optional)");
        VBox box = new VBox(12, title, text, reason, new HBox(8,
                button(confirm, "danger-button", () -> {
                    Modal.close();
                    action.accept(reason.getText().trim());
                }),
                button("Cancel", "secondary-button", Modal::close)));
        box.getStyleClass().add("modal-card");
        Modal.show(box, 460);
    }

    // ------------------------------------------------------------------ Reports

    private void showReports(JsonArray reports) {
        body.getChildren().clear();
        if (reports.isEmpty()) {
            body.getChildren().add(People.empty(Icons.ERROR_SCAN, "No open reports", "All clear."));
        }
        for (JsonElement e : reports) {
            JsonObject r = e.getAsJsonObject();
            long id = r.get("id").getAsLong();
            String target = r.get("target").getAsString();
            String targetName = r.get("targetName").getAsString();
            String details = r.get("details").isJsonNull() ? "" : " · " + r.get("details").getAsString();
            body.getChildren().add(People.row(target, targetName,
                    "Reported by " + r.get("reporterName").getAsString() + details, null, r.get("reason").getAsString(),
                    List.of(People.iconButton(Icons.BAN, "Ban " + targetName, false,
                                    () -> banDialog(target, targetName, this::show)),
                            button("Close", "secondary-button", () -> call(() -> online.post(
                                    "/api/admin/reports/" + id + "/close", null), x -> show())))));
        }
        Animations.flipInCards(body);
    }

    // ------------------------------------------------------------------ Events

    private void showEvents(JsonArray events) {
        TextField name = new TextField();
        name.setPromptText("Event name (e.g. Double Honor Weekend)");
        HBox.setHgrow(name, Priority.ALWAYS);
        ComboBox<String> multiplier = new ComboBox<>();
        multiplier.getItems().setAll("x1.5", "x2", "x3", "x5");
        multiplier.setValue("x2");
        multiplier.getStyleClass().add("form-combo");
        ComboBox<String> length = new ComboBox<>();
        Map<String, Integer> hours = new LinkedHashMap<>();
        hours.put("2 hours", 2);
        hours.put("1 day", 24);
        hours.put("Weekend (2 days)", 48);
        hours.put("1 week", 168);
        length.getItems().setAll(hours.keySet());
        length.setValue("1 day");
        length.getStyleClass().add("form-combo");
        Button start = button("Start now", "primary-button", () -> {
            Instant now = Instant.now();
            call(() -> online.post("/api/admin/events", Map.of("name", name.getText().trim(),
                    "multiplier", Double.parseDouble(multiplier.getValue().substring(1)),
                    "startsAt", now.toString(), "endsAt", now.plus(Duration.ofHours(hours.get(length.getValue()))).toString())),
                    r -> {
                        Toast.info("Event started");
                        show();
                    });
        });
        Label heading = new Label("New honor event");
        heading.getStyleClass().add("section-title");
        VBox form = new VBox(10, heading, new HBox(8, name, multiplier, length, start));
        form.getStyleClass().add("settings-card");
        body.getChildren().setAll(form);
        for (JsonElement e : events) {
            JsonObject ev = e.getAsJsonObject();
            long id = ev.get("id").getAsLong();
            Label mult = new Label("x" + ev.get("multiplier").getAsDouble());
            mult.getStyleClass().add("event-multiplier");
            Label title = new Label(ev.get("name").getAsString());
            title.getStyleClass().add("mod-title");
            Label when = new Label(shortTime(ev.get("startsAt").getAsString()) + "  →  " + shortTime(ev.get("endsAt").getAsString()));
            when.getStyleClass().add("mod-meta");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(14, mult, new VBox(2, title, when), spacer,
                    People.iconButton(Icons.CLOSE, "Delete event", false, () -> call(
                            () -> online.delete("/api/admin/events/" + id), r -> show())));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mod-row");
            body.getChildren().add(row);
        }
        Animations.flipInCards(body);
    }

    // ------------------------------------------------------------------ Partners

    private void showPartners(JsonArray partners) {
        TextField name = new TextField();
        name.setPromptText("Server name");
        TextField address = new TextField();
        address.setPromptText("Address (play.example.net)");
        TextField manager = new TextField();
        manager.setPromptText("Manager's Minecraft UUID");
        TextField description = new TextField();
        description.setPromptText("Short description");
        HBox.setHgrow(description, Priority.ALWAYS);
        Button add = button("Add partner", "primary-button", () -> call(() -> online.post("/api/admin/partners",
                Map.of("name", name.getText().trim(), "address", address.getText().trim(),
                        "description", description.getText().trim(), "manager", manager.getText().trim())), r -> {
            Toast.info("Partner added");
            show();
        }));
        Label heading = new Label("New partner server");
        heading.getStyleClass().add("section-title");
        VBox form = new VBox(10, heading, new HBox(8, name, address, manager), new HBox(8, description, add));
        form.getStyleClass().add("settings-card");
        body.getChildren().setAll(form);
        for (JsonElement e : partners) {
            JsonObject p = e.getAsJsonObject();
            long id = p.get("id").getAsLong();
            int blocked = p.getAsJsonArray("blockedMods").size();
            body.getChildren().add(People.row(null, p.get("name").getAsString(),
                    p.get("address").getAsString() + "  ·  " + blocked + " mods switched off there", null, "PARTNER",
                    List.of(People.iconButton(Icons.CLOSE, "Remove partner", false, () -> Modal.confirm(
                            "Remove " + p.get("name").getAsString() + "?", "It stops being a Nexus Partner.", "Remove",
                            () -> call(() -> online.delete("/api/admin/partners/" + id), r -> show()))))));
        }
        Animations.flipInCards(body);
    }

    // ------------------------------------------------------------------ Crashes

    private void showCrashes(JsonArray crashes) {
        body.getChildren().clear();
        if (crashes.isEmpty()) {
            body.getChildren().add(People.empty(Icons.ERROR_SCAN, "No crash reports", "Nice."));
        }
        for (JsonElement e : crashes) {
            JsonObject c = e.getAsJsonObject();
            String report = c.get("report").getAsString();
            String firstLine = report.lines().filter(l -> !l.isBlank()).findFirst().orElse("(empty)");
            Label title = new Label(firstLine.length() > 90 ? firstLine.substring(0, 90) + "…" : firstLine);
            title.getStyleClass().add("mod-title");
            Label meta = new Label("Nexus " + c.get("appVersion").getAsString() + "  ·  " + shortTime(c.get("createdAt").getAsString()));
            meta.getStyleClass().add("mod-meta");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(12, new VBox(2, title, meta), spacer,
                    button("View", "secondary-button", () -> {
                        TextArea text = new TextArea(report);
                        text.setEditable(false);
                        text.setPrefHeight(420);
                        VBox box = new VBox(10, text, new HBox(8, button("Copy", "primary-button", () -> copy(report)),
                                button("Close", "secondary-button", Modal::close)));
                        box.getStyleClass().add("modal-card");
                        Modal.show(box, 760);
                    }));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mod-row");
            body.getChildren().add(row);
        }
        Animations.flipInCards(body);
    }

    // ------------------------------------------------------------------ small pieces

    private interface Call {
        JsonElement run() throws Exception;
    }

    private void call(Call job, Consumer<JsonElement> whenDone) {
        Background.run("developer", job::run, whenDone, error -> Toast.error(error.getMessage()));
    }

    private static Button button(String text, String style, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static void copy(String text) {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        Toast.info("Copied");
    }

    private static void openFolder(java.nio.file.Path folder) {
        try {
            java.nio.file.Files.createDirectories(folder);
            java.awt.Desktop.getDesktop().open(folder.toFile());
        } catch (Exception e) {
            Toast.error("Couldn't open " + folder);
        }
    }

    /** "2026-10-10T18:22:01.123Z" → "2026-10-10 18:22" */
    private static String shortTime(String iso) {
        String t = iso.replace('T', ' ');
        return t.length() >= 16 ? t.substring(0, 16) : t;
    }
}
