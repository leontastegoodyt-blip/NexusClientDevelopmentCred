package com.nexusclient.launcher.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.online.NexusOnline;
import com.nexusclient.launcher.servers.ServerStore;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * FriendsPage – the Friends tab: who's online and on which server (one-click Join), friend requests,
 * direct messages, parties with party chat, and blocking. Everything comes from the Nexus online
 * server through NexusOnline, and updates live.
 */
public class FriendsPage extends VBox {

    private final NexusOnline online = NexusOnline.get();
    private final AccountManager accounts;
    private final Consumer<ServerStore.Server> onJoin;
    private final VBox body = new VBox(10);
    private final Label status = new Label();
    private final List<String> partyChat = new ArrayList<>();
    private JsonObject overview;
    private JsonObject party;
    private String currentTab = "Online";
    private String pendingInviteParty;
    private String pendingInviteFrom;
    private DmWindow openDm;

    public FriendsPage(AccountManager accounts, Consumer<ServerStore.Server> onJoin) {
        this.accounts = accounts;
        this.onJoin = onJoin;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.FRIENDS.title());
        title.getStyleClass().add("page-title");
        Label description = new Label(Page.FRIENDS.description());
        description.getStyleClass().add("page-description");
        status.getStyleClass().add("mod-meta");

        TextField addName = new TextField();
        addName.setPromptText("Add a friend by Minecraft name");
        addName.setPrefWidth(240);
        Button add = new Button("Add friend");
        add.getStyleClass().add("primary-button");
        add.setMinWidth(Region.USE_PREF_SIZE);
        Runnable sendRequest = () -> {
            String name = addName.getText().trim();
            if (!name.isEmpty()) {
                call(() -> online.post("/api/friends/request", Map.of("name", name)), result -> {
                    addName.clear();
                    Toast.info(result.getAsJsonObject().get("result").getAsString().equals("accepted")
                            ? "You're now friends with " + name : "Friend request sent to " + name);
                    refresh();
                });
            }
        };
        add.setOnAction(e -> sendRequest.run());
        addName.setOnAction(e -> sendRequest.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, new VBox(4, title, description, status), spacer, addName, add);
        header.setAlignment(Pos.CENTER_LEFT);

        TabStrip tabs = new TabStrip(Page.FRIENDS.tabs(), tab -> {
            currentTab = tab;
            show();
        });

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, tabs, scroll);

        online.addStateListener(state -> showStatus());
        online.addListener(this::liveEvent);
        showStatus();
        connectAndLoad();
    }

    // ------------------------------------------------------------------ loading

    private void connectAndLoad() {
        body.getChildren().setAll(empty("Connecting to Nexus…", "Signing in to the Nexus online server."));
        Background.run("nexus-online", () -> {
            online.signIn(accounts);
            return true;
        }, ok -> refresh(), error -> {
            Button retry = new Button("Try again");
            retry.getStyleClass().add("secondary-button");
            retry.setOnAction(e -> connectAndLoad());
            body.getChildren().setAll(empty("Can't connect to Nexus", error.getMessage()), retry);
        });
    }

    private void refresh() {
        Background.run("friends", () -> new JsonElement[] {online.get("/api/friends"), online.get("/api/party")},
                result -> {
                    overview = result[0].getAsJsonObject();
                    JsonElement p = result[1].getAsJsonObject().get("party");
                    party = p == null || p.isJsonNull() ? null : p.getAsJsonObject();
                    show();
                }, error -> Toast.error(error.getMessage()));
    }

    private void showStatus() {
        status.setText(switch (online.state()) {
            case ONLINE -> "● Online as " + online.myName();
            case CONNECTING -> "Connecting…";
            case OFFLINE -> "Offline – trying to reconnect";
            case SIGNED_OUT -> "Not signed in";
        });
    }

    // ------------------------------------------------------------------ tabs

    private void show() {
        if (overview == null) {
            return;
        }
        body.getChildren().clear();
        switch (currentTab) {
            case "Online", "All Friends" -> showFriends(currentTab.equals("Online"));
            case "Requests" -> showRequests();
            case "Parties" -> showParty();
            case "Blocked" -> showBlocked();
            default -> { }
        }
    }

    private void showFriends(boolean onlineOnly) {
        JsonArray friends = overview.getAsJsonArray("friends");
        int shown = 0;
        for (JsonElement element : friends) {
            JsonObject friend = element.getAsJsonObject();
            if (onlineOnly && !friend.get("online").getAsBoolean()) {
                continue;
            }
            body.getChildren().add(friendRow(friend));
            shown++;
        }
        if (shown == 0) {
            body.getChildren().add(onlineOnly
                    ? empty("No friends online", "Friends who are playing show up here, with the server they're on.")
                    : empty("No friends yet", "Add friends by their Minecraft name at the top."));
        }
    }

    private Node friendRow(JsonObject friend) {
        String uuid = friend.get("uuid").getAsString();
        String name = friend.get("name").getAsString();
        boolean isOnline = friend.get("online").getAsBoolean();
        String server = friend.has("server") && !friend.get("server").isJsonNull() ? friend.get("server").getAsString() : "";

        List<Node> buttons = new ArrayList<>();
        if (!server.isEmpty()) {
            buttons.add(button("Join", "primary-button", () -> onJoin.accept(new ServerStore.Server(server, server, ""))));
        }
        buttons.add(button("Message", "secondary-button", () -> openDm(uuid, name)));
        if (isOnline) {
            buttons.add(button("Invite to party", "secondary-button", () -> call(
                    () -> online.post("/api/party/invite", Map.of("uuid", uuid)),
                    ok -> Toast.info("Party invite sent to " + name))));
        }
        buttons.add(button("Remove", "secondary-button", () -> Modal.confirm("Remove " + name + "?",
                "You'll stop being friends. You can add them again later.", "Remove",
                () -> call(() -> online.post("/api/friends/remove", Map.of("uuid", uuid)), ok -> refresh()))));
        buttons.add(button("Block", "danger-button", () -> Modal.confirm("Block " + name + "?",
                "They won't be able to message you, invite you or add you as a friend.", "Block",
                () -> call(() -> online.post("/api/friends/block", Map.of("uuid", uuid)), ok -> refresh()))));

        String detail = !isOnline ? "Offline" : server.isEmpty() ? "Online" : "Playing on " + server;
        return row(name, detail, isOnline, buttons);
    }

    private void showRequests() {
        JsonArray incoming = overview.getAsJsonArray("incoming");
        JsonArray outgoing = overview.getAsJsonArray("outgoing");
        body.getChildren().add(section("Waiting for you (" + incoming.size() + ")"));
        for (JsonElement e : incoming) {
            String uuid = e.getAsJsonObject().get("uuid").getAsString();
            String name = e.getAsJsonObject().get("name").getAsString();
            body.getChildren().add(row(name, "Wants to be friends", false, List.of(
                    button("Accept", "primary-button", () -> call(
                            () -> online.post("/api/friends/accept", Map.of("uuid", uuid)), ok -> refresh())),
                    button("Decline", "secondary-button", () -> call(
                            () -> online.post("/api/friends/decline", Map.of("uuid", uuid)), ok -> refresh())))));
        }
        body.getChildren().add(section("Sent (" + outgoing.size() + ")"));
        for (JsonElement e : outgoing) {
            String uuid = e.getAsJsonObject().get("uuid").getAsString();
            body.getChildren().add(row(e.getAsJsonObject().get("name").getAsString(), "Waiting for an answer", false,
                    List.of(button("Cancel", "secondary-button", () -> call(
                            () -> online.post("/api/friends/decline", Map.of("uuid", uuid)), ok -> refresh())))));
        }
    }

    private void showParty() {
        if (pendingInviteParty != null) {
            body.getChildren().add(row(pendingInviteFrom + " invited you to a party", "Invites last 60 seconds", true,
                    List.of(button("Join party", "primary-button", () -> {
                        String id = pendingInviteParty;
                        pendingInviteParty = null;
                        call(() -> online.post("/api/party/accept", Map.of("partyId", id)), ok -> refresh());
                    }))));
        }
        if (party == null) {
            body.getChildren().add(empty("Not in a party",
                    "Invite an online friend from the Online tab. Parties get their own chat, shared waypoints, "
                            + "and the leader can bring everyone to the same server."));
            return;
        }
        String leader = party.get("leader").getAsString();
        boolean iLead = leader.equals(online.myUuid());
        body.getChildren().add(section("Party (" + party.getAsJsonArray("members").size() + "/8)"));
        for (JsonElement e : party.getAsJsonArray("members")) {
            JsonObject m = e.getAsJsonObject();
            String uuid = m.get("uuid").getAsString();
            String server = m.get("server").getAsString();
            List<Node> buttons = new ArrayList<>();
            if (iLead && !uuid.equals(online.myUuid())) {
                buttons.add(button("Make leader", "secondary-button", () -> call(
                        () -> online.post("/api/party/promote", Map.of("uuid", uuid)), ok -> refresh())));
                buttons.add(button("Kick", "danger-button", () -> call(
                        () -> online.post("/api/party/kick", Map.of("uuid", uuid)), ok -> refresh())));
            }
            String detail = (uuid.equals(leader) ? "★ Leader · " : "") + (server.isEmpty() ? "Online" : "On " + server);
            body.getChildren().add(row(m.get("name").getAsString(), detail, m.get("online").getAsBoolean(), buttons));
        }

        // Party chat.
        VBox chatLines = new VBox(4);
        for (String line : partyChat) {
            Label label = new Label(line);
            label.getStyleClass().add("torcher-text");
            label.setWrapText(true);
            chatLines.getChildren().add(label);
        }
        if (partyChat.isEmpty()) {
            Label hint = new Label("Party chat is empty. Messages here also show in-game.");
            hint.getStyleClass().add("mod-meta");
            chatLines.getChildren().add(hint);
        }
        TextField input = new TextField();
        input.setPromptText("Message your party");
        HBox.setHgrow(input, Priority.ALWAYS);
        Runnable sendChat = () -> {
            String text = input.getText().trim();
            if (!text.isEmpty()) {
                input.clear();
                call(() -> online.post("/api/party/chat", Map.of("text", text)), ok -> { });
            }
        };
        input.setOnAction(e -> sendChat.run());
        HBox sendRow = new HBox(8, input, button("Send", "primary-button", sendChat),
                button("Leave party", "danger-button", () -> call(() -> online.post("/api/party/leave", null), ok -> {
                    partyChat.clear();
                    refresh();
                })));
        VBox chat = new VBox(10, section("Party chat"), chatLines, sendRow);
        chat.getStyleClass().add("settings-card");
        body.getChildren().add(chat);
    }

    private void showBlocked() {
        JsonArray blocked = overview.getAsJsonArray("blocked");
        if (blocked.isEmpty()) {
            body.getChildren().add(empty("Nobody blocked", "Blocked players can't message, invite or add you."));
        }
        for (JsonElement e : blocked) {
            String uuid = e.getAsJsonObject().get("uuid").getAsString();
            body.getChildren().add(row(e.getAsJsonObject().get("name").getAsString(), "Blocked", false,
                    List.of(button("Unblock", "secondary-button", () -> call(
                            () -> online.post("/api/friends/unblock", Map.of("uuid", uuid)), ok -> refresh())))));
        }
    }

    // ------------------------------------------------------------------ live events

    private void liveEvent(JsonObject event) {
        String type = event.get("type").getAsString();
        switch (type) {
            case "friend.request" -> {
                Toast.info(event.get("name").getAsString() + " sent you a friend request");
                refresh();
            }
            case "friend.accepted", "friend.removed", "friend.presence", "party.update", "party.left" -> refresh();
            case "dm" -> {
                JsonObject message = event.getAsJsonObject("message");
                String from = message.get("from").getAsString();
                if (openDm != null && openDm.uuid.equals(from)) {
                    openDm.add(event.get("fromName").getAsString(), message.get("text").getAsString());
                } else {
                    Toast.info("Message from " + event.get("fromName").getAsString() + ": " + message.get("text").getAsString());
                }
            }
            case "party.invite" -> {
                pendingInviteParty = event.get("partyId").getAsString();
                pendingInviteFrom = event.get("fromName").getAsString();
                Toast.info(pendingInviteFrom + " invited you to a party – see Friends → Parties");
                if (currentTab.equals("Parties")) {
                    show();
                }
            }
            case "party.chat" -> {
                partyChat.add(event.get("fromName").getAsString() + ": " + event.get("text").getAsString());
                if (partyChat.size() > 100) {
                    partyChat.remove(0);
                }
                if (currentTab.equals("Parties")) {
                    show();
                }
            }
            case "party.warp" -> {
                String server = event.get("server").getAsString();
                Modal.confirm("Join your party?", event.get("fromName").getAsString() + " wants the party to join "
                        + server + ".", "Join", () -> onJoin.accept(new ServerStore.Server(server, server, "")));
            }
            case "notice" -> Toast.info(event.get("text").getAsString());
            default -> { }
        }
    }

    // ------------------------------------------------------------------ direct messages

    private void openDm(String uuid, String name) {
        DmWindow window = new DmWindow(uuid, name);
        openDm = window;
        Modal.show(window.view, 520);
        call(() -> online.get("/api/messages/" + uuid), messages -> {
            List<JsonElement> list = new ArrayList<>();
            messages.getAsJsonArray().forEach(list::add);
            java.util.Collections.reverse(list); // oldest first
            for (JsonElement e : list) {
                JsonObject m = e.getAsJsonObject();
                window.add(m.get("from").getAsString().equals(online.myUuid()) ? "You" : name, m.get("text").getAsString());
            }
            call(() -> online.post("/api/messages/" + uuid + "/read", null), ok -> { });
        });
    }

    /** The pop-up conversation with one friend. */
    private final class DmWindow {
        final String uuid;
        final VBox lines = new VBox(4);
        final VBox view;

        DmWindow(String uuid, String name) {
            this.uuid = uuid;
            Label title = new Label("Messages with " + name);
            title.getStyleClass().add("form-title");
            ScrollPane scroll = new ScrollPane(lines);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(300);
            scroll.getStyleClass().add("page-scroll");
            lines.heightProperty().addListener((obs, old, now) -> scroll.setVvalue(1.0));
            TextField input = new TextField();
            input.setPromptText("Message " + name);
            HBox.setHgrow(input, Priority.ALWAYS);
            Runnable send = () -> {
                String text = input.getText().trim();
                if (!text.isEmpty()) {
                    input.clear();
                    call(() -> online.post("/api/messages/" + uuid, Map.of("text", text)),
                            sent -> add("You", sent.getAsJsonObject().get("text").getAsString()));
                }
            };
            input.setOnAction(e -> send.run());
            Button close = button("Close", "secondary-button", () -> {
                openDm = null;
                Modal.close();
            });
            view = new VBox(12, title, scroll, new HBox(8, input, button("Send", "primary-button", send), close));
            view.getStyleClass().add("modal-card");
        }

        void add(String who, String text) {
            Label line = new Label(who + ": " + text);
            line.getStyleClass().add("torcher-text");
            line.setWrapText(true);
            lines.getChildren().add(line);
        }
    }

    // ------------------------------------------------------------------ small pieces

    /** Runs a network call in the background; errors are shown as a red toast. */
    private interface Call {
        JsonElement run() throws Exception;
    }

    private void call(Call job, Consumer<JsonElement> whenDone) {
        Background.run("friends-call", job::run, whenDone, error -> Toast.error(error.getMessage()));
    }

    private static Node row(String name, String detail, boolean highlighted, List<Node> buttons) {
        Label initial = new Label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        initial.getStyleClass().add("instance-badge");
        initial.setMinSize(36, 36);
        initial.setAlignment(Pos.CENTER);
        Label title = new Label(name);
        title.getStyleClass().add("mod-title");
        Label meta = new Label((highlighted ? "● " : "") + detail);
        meta.getStyleClass().add("mod-meta");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, initial, new VBox(2, title, meta), spacer);
        row.getChildren().addAll(buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        return row;
    }

    private static Button button(String text, String style, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Node empty(String title, String text) {
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        Label d = new Label(text);
        d.getStyleClass().add("empty-text");
        d.setWrapText(true);
        VBox box = new VBox(6, t, d);
        box.getStyleClass().add("settings-card");
        return box;
    }
}
