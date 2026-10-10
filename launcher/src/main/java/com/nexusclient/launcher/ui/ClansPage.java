package com.nexusclient.launcher.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.online.NexusOnline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ClansPage – the Clans tab: your clan (members, ranks, honor, clan chat, polls), clan invites, the
 * honor leaderboard (Nexus MVP on top, podium = free Premium), honor events, and clan search.
 */
public class ClansPage extends VBox {

    private static final List<String> RANKS = List.of("CO_OWNER", "FIRST_CLASS", "VIP", "MEMBER");

    private final NexusOnline online = NexusOnline.get();
    private final AccountManager accounts;
    private final VBox body = new VBox(10);
    private String currentTab = "My Clan";

    public ClansPage(AccountManager accounts) {
        this.accounts = accounts;
        getStyleClass().add("tab-page");
        Label title = new Label(Page.CLANS.title());
        title.getStyleClass().add("page-title");
        Button refresh = button("Refresh", "secondary-button", this::show);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, PageHeader.block(Page.CLANS, title), spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        TabStrip tabs = new TabStrip(Page.CLANS.tabs(), tab -> {
            currentTab = tab;
            show();
        });
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, tabs, scroll);

        online.addListener(event -> {
            String type = event.get("type").getAsString();
            if (type.equals("clan.invite")) {
                Toast.info("You're invited to the clan " + event.get("clanName").getAsString() + " – see Clans");
            }
            if (type.startsWith("clan.") && isVisible()) {
                show();
            }
        });
        body.getChildren().setAll(People.empty(Icons.CLANS, "Connecting…", null));
        Background.run("nexus-online", () -> {
            online.signIn(accounts);
            return true;
        }, ok -> show(), error -> body.getChildren().setAll(People.empty(Icons.CLANS, "Can't connect", error.getMessage()),
                button("Try again", "secondary-button", () -> {
                    body.getChildren().clear();
                    Background.run("nexus-online", () -> {
                        online.signIn(accounts);
                        return true;
                    }, ok2 -> show(), e2 -> Toast.error(e2.getMessage()));
                })));
    }

    private void show() {
        if (!online.signedIn()) {
            return;
        }
        switch (currentTab) {
            case "My Clan" -> call(() -> new JsonElement[] {online.get("/api/clans/mine"), online.get("/api/clans/invites")},
                    r -> showMyClan(r[0].getAsJsonArray(), r[1].getAsJsonArray()));
            case "Leaderboard" -> call(() -> new JsonElement[] {online.get("/api/clans/leaderboard")},
                    r -> showClanList(r[0].getAsJsonArray(), "No clans yet – be the first to make one!"));
            case "Events" -> call(() -> new JsonElement[] {online.get("/api/events")}, r -> showEvents(r[0].getAsJsonArray()));
            case "Browse" -> showBrowse();
            default -> { }
        }
    }

    // ------------------------------------------------------------------ My Clan

    private void showMyClan(JsonArray clans, JsonArray invites) {
        body.getChildren().clear();
        for (JsonElement e : invites) {
            JsonObject invite = e.getAsJsonObject();
            long id = invite.get("clanId").getAsLong();
            body.getChildren().add(People.row(null, "[" + invite.get("tag").getAsString() + "] "
                            + invite.get("clanName").getAsString(), "Invited you to join", null, "INVITE", List.of(
                            button("Join", "primary-button", () -> post("/api/clans/" + id + "/accept", null, this::show)),
                            People.iconButton(Icons.CLOSE, "Decline", false,
                                    () -> post("/api/clans/" + id + "/decline", null, this::show)))));
        }
        if (clans.isEmpty()) {
            body.getChildren().add(createForm());
        }
        for (JsonElement e : clans) {
            body.getChildren().add(clanCard(e.getAsJsonObject()));
        }
        Animations.flipInCards(body);
    }

    private Node createForm() {
        TextField name = new TextField();
        name.setPromptText("Clan name");
        TextField tag = new TextField();
        tag.setPromptText("TAG");
        tag.setPrefWidth(90);
        HBox.setHgrow(name, Priority.ALWAYS);
        Button create = button("Create clan", "primary-button", () -> post("/api/clans",
                Map.of("name", name.getText().trim(), "tag", tag.getText().trim()), () -> {
                    Toast.info("Clan created!");
                    show();
                }));
        Label title = new Label("Start a clan");
        title.getStyleClass().add("section-title");
        HBox facts = People.statChips("100 honor / hour", "35 members", "Top 3 = free Premium");
        VBox card = new VBox(12, new HBox(14, emblem("?"), new VBox(8, title, facts)), new HBox(8, name, tag, create));
        card.getStyleClass().add("settings-card");
        return card;
    }

    /** The clan's emblem: its tag in a glowing hexagon badge. */
    private static StackPane emblem(String tag) {
        Label letters = new Label(tag);
        letters.getStyleClass().add("clan-emblem-text");
        StackPane emblem = new StackPane(letters);
        emblem.getStyleClass().add("clan-emblem");
        emblem.setMinSize(64, 64);
        emblem.setMaxSize(64, 64);
        return emblem;
    }

    private Node clanCard(JsonObject clan) {
        long id = clan.get("id").getAsLong();
        String myRank = clan.get("myRank").getAsString();
        boolean canManage = myRank.equals("OWNER") || myRank.equals("CO_OWNER") || myRank.equals("FIRST_CLASS");
        JsonArray members = clan.getAsJsonArray("members");

        // --- Banner: emblem, name, live numbers ---
        Label title = new Label(clan.get("name").getAsString());
        title.getStyleClass().add("clan-name");
        int place = clan.get("place").getAsInt();
        String placeText = clan.get("mvp").getAsBoolean() ? "★ Nexus MVP" : place == 0 ? "Unranked"
                : (place <= 3 ? medal(place) + " " : "") + "#" + place;
        long online = 0;
        for (JsonElement e : members) {
            if (e.getAsJsonObject().get("online").getAsBoolean()) {
                online++;
            }
        }
        Label honor = new Label();
        honor.getStyleClass().add("clan-honor");
        Animations.countUp(honor, clan.get("honor").getAsLong(), " honor");
        HBox chips = People.statChips(placeText, members.size() + "/35 members", "● " + online + " online",
                "You: " + pretty(myRank));
        VBox words = new VBox(4, title, honor, chips);
        HBox banner = new HBox(16, emblem(clan.get("tag").getAsString()), words);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("clan-banner");
        if (clan.get("podium").getAsBoolean()) {
            Label premium = new Label("PODIUM · FREE PREMIUM");
            premium.getStyleClass().add("person-badge");
            words.getChildren().add(1, premium);
        }

        // --- Actions: invite field + a ⋯ menu for everything else ---
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (canManage) {
            TextField inviteName = new TextField();
            inviteName.setPromptText("Invite by name");
            HBox.setHgrow(inviteName, Priority.ALWAYS);
            Runnable invite = () -> post("/api/clans/" + id + "/invite",
                    Map.of("name", inviteName.getText().trim()), () -> Toast.info("Invite sent"));
            inviteName.setOnAction(e -> invite.run());
            actions.getChildren().addAll(inviteName, People.iconButton(Icons.ADD, "Invite", true, invite));
        } else {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            actions.getChildren().add(spacer);
        }
        java.util.Map<String, Runnable> more = new java.util.LinkedHashMap<>();
        if (!clan.get("primary").getAsBoolean()) {
            more.put("Send my honor here", () -> post("/api/clans/" + id + "/primary", null, this::show));
        }
        if (!myRank.equals("MEMBER")) {
            more.put("Edit description", () -> editDescription(id));
        }
        if (myRank.equals("OWNER")) {
            more.put("!Delete clan", () -> Modal.confirm("Delete the clan?",
                    "Everyone is removed and its honor is lost. This can't be undone.", "Delete",
                    () -> call(() -> new JsonElement[] {online().delete("/api/clans/" + id)}, r -> show())));
        } else {
            more.put("!Leave clan", () -> Modal.confirm("Leave the clan?",
                    "You can only come back if someone invites you again.", "Leave",
                    () -> post("/api/clans/" + id + "/leave", null, this::show)));
        }
        actions.getChildren().add(People.moreMenu(more));

        VBox card = new VBox(14, banner);
        if (clan.has("description") && !clan.get("description").isJsonNull()
                && !clan.get("description").getAsString().isBlank()) {
            Label d = new Label(clan.get("description").getAsString());
            d.getStyleClass().add("torcher-text");
            d.setWrapText(true);
            card.getChildren().add(d);
        }
        card.getChildren().add(actions);

        // --- Members, highest rank first ---
        card.getChildren().add(section("Members"));
        List<JsonObject> sorted = new ArrayList<>();
        members.forEach(e -> sorted.add(e.getAsJsonObject()));
        sorted.sort(java.util.Comparator.comparingInt((JsonObject m) -> rankIndex(m.get("rank").getAsString()))
                .thenComparing(m -> -m.get("honor").getAsLong()));
        VBox memberList = new VBox(8);
        for (JsonObject m : sorted) {
            memberList.getChildren().add(memberRow(id, m, myRank));
        }
        card.getChildren().add(memberList);
        if (clan.has("invited") && !clan.getAsJsonArray("invited").isEmpty()) {
            List<String> names = new ArrayList<>();
            clan.getAsJsonArray("invited").forEach(n -> names.add(n.getAsString()));
            Label invited = new Label("Invited: " + String.join(", ", names));
            invited.getStyleClass().add("mod-meta");
            card.getChildren().add(invited);
        }

        card.getChildren().addAll(chatBox(id), pollsBox(id, !myRank.equals("MEMBER")));
        card.getStyleClass().add("settings-card");
        return card;
    }

    private NexusOnline online() {
        return online;
    }

    private Node memberRow(long clanId, JsonObject m, String myRank) {
        String uuid = m.get("uuid").getAsString();
        String rank = m.get("rank").getAsString();
        String name = m.get("name").getAsString();
        boolean me = uuid.equals(online.myUuid());
        int mine = rankIndex(myRank);
        int theirs = rankIndex(rank);
        boolean canManage = mine <= 2 && mine < theirs && !me; // owner, co-owner, first class – over lower ranks

        List<Node> buttons = new ArrayList<>();
        java.util.Map<String, Runnable> menu = new java.util.LinkedHashMap<>();
        if (canManage) {
            for (String r : RANKS) {
                if (rankIndex(r) > mine && !r.equals(rank)) {
                    menu.put("Make " + pretty(r), () -> post("/api/clans/" + clanId + "/rank",
                            Map.of("uuid", uuid, "rank", r), this::show));
                }
            }
        }
        if (myRank.equals("OWNER") && !me) {
            menu.put("Give clan to " + name, () -> Modal.confirm("Give the clan to " + name + "?",
                    "They become the owner and you take their rank.", "Give clan",
                    () -> post("/api/clans/" + clanId + "/transfer", Map.of("uuid", uuid), this::show)));
        }
        if (canManage) {
            menu.put("!Kick", () -> post("/api/clans/" + clanId + "/kick", Map.of("uuid", uuid), this::show));
        }
        if (!menu.isEmpty()) {
            buttons.add(People.moreMenu(menu));
        }
        HBox row = People.row(uuid, name + (me ? " (you)" : ""), m.get("honor").getAsLong() + " honor",
                m.get("online").getAsBoolean(), pretty(rank).toUpperCase(), buttons);
        row.getStyleClass().add("rank-" + rank.toLowerCase());
        return row;
    }

    private static String medal(int place) {
        return place == 1 ? "🥇" : place == 2 ? "🥈" : "🥉";
    }

    private Node chatBox(long clanId) {
        VBox lines = new VBox(3);
        call(() -> new JsonElement[] {online.get("/api/clans/" + clanId + "/chat")}, r -> {
            JsonArray list = r[0].getAsJsonArray();
            int start = Math.max(0, list.size() - 20);
            for (int i = start; i < list.size(); i++) {
                JsonObject msg = list.get(i).getAsJsonObject();
                Label line = new Label(msg.get("fromName").getAsString() + ": " + msg.get("text").getAsString());
                line.getStyleClass().add("torcher-text");
                line.setWrapText(true);
                lines.getChildren().add(line);
            }
            if (list.isEmpty()) {
                Label hint = new Label("No messages yet.");
                hint.getStyleClass().add("mod-meta");
                lines.getChildren().add(hint);
            }
        });
        TextField input = new TextField();
        input.setPromptText("Message your clan");
        HBox.setHgrow(input, Priority.ALWAYS);
        Runnable send = () -> {
            String text = input.getText().trim();
            if (!text.isEmpty()) {
                input.clear();
                post("/api/clans/" + clanId + "/chat", Map.of("text", text), () -> { });
            }
        };
        input.setOnAction(e -> send.run());
        return new VBox(8, section("Clan chat"), lines, new HBox(8, input, button("Send", "primary-button", send)));
    }

    private Node pollsBox(long clanId, boolean canCreate) {
        VBox box = new VBox(8, section("Polls"));
        call(() -> new JsonElement[] {online.get("/api/clans/" + clanId + "/polls")}, r -> {
            for (JsonElement e : r[0].getAsJsonArray()) {
                JsonObject poll = e.getAsJsonObject();
                long pollId = poll.get("id").getAsLong();
                boolean open = poll.get("open").getAsBoolean();
                JsonArray options = poll.getAsJsonArray("options");
                JsonArray votes = poll.getAsJsonArray("votes");
                Integer myVote = poll.has("myVote") && !poll.get("myVote").isJsonNull() ? poll.get("myVote").getAsInt() : null;
                HBox choices = new HBox(8);
                for (int i = 0; i < options.size(); i++) {
                    int choice = i;
                    String label = options.get(i).getAsString() + " (" + votes.get(i).getAsInt() + ")"
                            + (myVote != null && myVote == i ? " ✓" : "");
                    Button b = button(label, myVote != null && myVote == i ? "primary-button" : "secondary-button",
                            () -> post("/api/clans/polls/" + pollId + "/vote", Map.of("choice", choice), this::show));
                    b.setDisable(!open);
                    choices.getChildren().add(b);
                }
                Label q = new Label(poll.get("question").getAsString() + (open ? "" : " (closed)"));
                q.getStyleClass().add("mod-title");
                box.getChildren().addAll(q, choices);
            }
            if (canCreate) {
                TextField question = new TextField();
                question.setPromptText("New poll question");
                TextField options = new TextField();
                options.setPromptText("Options, separated by commas (2-4)");
                HBox.setHgrow(options, Priority.ALWAYS);
                box.getChildren().add(new HBox(8, question, options, button("Start poll", "secondary-button", () -> {
                    List<String> list = new ArrayList<>();
                    for (String o : options.getText().split(",")) {
                        list.add(o.trim());
                    }
                    post("/api/clans/" + clanId + "/polls", Map.of("question", question.getText().trim(), "options", list), this::show);
                })));
            }
        });
        return box;
    }

    private void editDescription(long clanId) {
        TextField text = new TextField();
        text.setPromptText("What's your clan about? (max 300 characters)");
        VBox box = new VBox(12, section("Clan description"), text, new HBox(8,
                button("Save", "primary-button", () -> post("/api/clans/" + clanId + "/settings",
                        Map.of("description", text.getText()), () -> {
                            Modal.close();
                            show();
                        })),
                button("Cancel", "secondary-button", Modal::close)));
        box.getStyleClass().add("modal-card");
        Modal.show(box, 480);
    }

    // ------------------------------------------------------------------ other tabs

    private void showClanList(JsonArray clans, String emptyText) {
        body.getChildren().clear();
        // The leaderboard starts with a podium for the top 3 (2nd · 1st · 3rd, like a real podium).
        List<JsonObject> podium = new ArrayList<>();
        for (JsonElement e : clans) {
            JsonObject c = e.getAsJsonObject();
            if (c.get("podium").getAsBoolean() && !c.get("mvp").getAsBoolean()) {
                podium.add(c);
            }
        }
        if (!podium.isEmpty()) {
            body.getChildren().add(podium(podium));
        }
        fillClanList(body, clans, emptyText);
        Animations.flipInCards(body);
    }

    /** Three blocks of different heights with the top clans standing on them. */
    private Node podium(List<JsonObject> top) {
        HBox stage = new HBox(14);
        stage.setAlignment(Pos.BOTTOM_CENTER);
        int[] order = {1, 0, 2}; // second, first, third
        double[] heights = {110, 80, 60};
        for (int index : order) {
            if (index >= top.size()) {
                continue;
            }
            JsonObject c = top.get(index);
            int place = c.get("place").getAsInt();
            Label medal = new Label(medal(place));
            medal.getStyleClass().add("podium-medal");
            Label name = new Label(c.get("name").getAsString());
            name.getStyleClass().add("mod-title");
            Label honor = new Label(c.get("honor").getAsLong() + " honor");
            honor.getStyleClass().add("mod-meta");
            Region block = new Region();
            block.getStyleClass().addAll("podium-block", "podium-" + place);
            block.setPrefSize(130, heights[index]);
            block.setMinHeight(heights[index]);
            Label number = new Label(String.valueOf(place));
            number.getStyleClass().add("podium-number");
            StackPane blockWithNumber = new StackPane(block, number);
            VBox column = new VBox(6, emblem(c.get("tag").getAsString()), medal, name, honor, blockWithNumber);
            column.setAlignment(Pos.BOTTOM_CENTER);
            column.getStyleClass().add("flip-in");
            stage.getChildren().add(column);
        }
        VBox box = new VBox(stage);
        box.getStyleClass().addAll("settings-card", "podium-card");
        return box;
    }

    private void fillClanList(VBox target, JsonArray clans, String emptyText) {
        if (clans.isEmpty()) {
            target.getChildren().add(People.empty(Icons.TROPHY, "No clans yet", emptyText));
        }
        for (JsonElement e : clans) {
            JsonObject c = e.getAsJsonObject();
            int place = c.get("place").getAsInt();
            String rank = c.get("mvp").getAsBoolean() ? "★" : place > 0 ? "#" + place : "–";
            Label rankLabel = new Label(rank);
            rankLabel.getStyleClass().add("leader-rank");
            rankLabel.setMinWidth(44);
            Label title = new Label(c.get("name").getAsString());
            title.getStyleClass().add("mod-title");
            Label meta = new Label(c.get("members").getAsLong() + " members");
            meta.getStyleClass().add("mod-meta");
            Label honor = new Label(c.get("honor").getAsLong() + " honor");
            honor.getStyleClass().add("leader-honor");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            StackPane small = emblem(c.get("tag").getAsString());
            small.setScaleX(0.62);
            small.setScaleY(0.62);
            StackPane emblemBox = new StackPane(small);
            emblemBox.setMinSize(42, 42);
            emblemBox.setMaxSize(42, 42);
            HBox row = new HBox(12, rankLabel, emblemBox, new VBox(2, title, meta), spacer, honor);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mod-row");
            if (c.get("mvp").getAsBoolean()) {
                row.getStyleClass().add("mvp-row");
            }
            target.getChildren().add(row);
        }
    }

    private void showEvents(JsonArray events) {
        body.getChildren().clear();
        if (events.isEmpty()) {
            body.getChildren().add(People.empty(Icons.BOLT, "No events right now", "Events multiply honor."));
        }
        for (JsonElement e : events) {
            JsonObject ev = e.getAsJsonObject();
            Label multiplier = new Label("x" + ev.get("multiplier").getAsDouble());
            multiplier.getStyleClass().add("event-multiplier");
            Label name = new Label(ev.get("name").getAsString());
            name.getStyleClass().add("mod-title");
            Label when = new Label(ev.get("startsAt").getAsString().replace('T', ' ').replace("Z", " UTC")
                    + "  →  " + ev.get("endsAt").getAsString().replace('T', ' ').replace("Z", " UTC"));
            when.getStyleClass().add("mod-meta");
            HBox row = new HBox(14, multiplier, new VBox(2, name, when));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mod-row");
            body.getChildren().add(row);
        }
        Animations.flipInCards(body);
    }

    private void showBrowse() {
        body.getChildren().clear();
        TextField search = new TextField();
        search.setPromptText("Search clans by name");
        HBox.setHgrow(search, Priority.ALWAYS);
        VBox results = new VBox(10);
        Runnable run = () -> call(() -> new JsonElement[] {online.get("/api/clans/search?q="
                        + java.net.URLEncoder.encode(search.getText().trim(), java.nio.charset.StandardCharsets.UTF_8))},
                r -> {
                    results.getChildren().clear();
                    fillClanList(results, r[0].getAsJsonArray(), "No clans match that name.");
                });
        search.setOnAction(e -> run.run());
        body.getChildren().addAll(new HBox(8, search, button("Search", "primary-button", run)), results);
    }

    // ------------------------------------------------------------------ small pieces

    private interface Calls {
        JsonElement[] run() throws Exception;
    }

    private void call(Calls job, Consumer<JsonElement[]> whenDone) {
        Background.run("clans", job::run, whenDone, error -> Toast.error(error.getMessage()));
    }

    private void post(String path, Object body, Runnable whenDone) {
        call(() -> new JsonElement[] {online.post(path, body)}, r -> whenDone.run());
    }

    private static int rankIndex(String rank) {
        return List.of("OWNER", "CO_OWNER", "FIRST_CLASS", "VIP", "MEMBER").indexOf(rank);
    }

    private static String pretty(String rank) {
        return switch (rank) {
            case "OWNER" -> "Owner";
            case "CO_OWNER" -> "Co-owner";
            case "FIRST_CLASS" -> "First Class";
            case "VIP" -> "VIP";
            default -> "Member";
        };
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
}
