package com.nexusclient.launcher.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.mods.ContentType;
import com.nexusclient.launcher.online.NexusOnline;
import com.nexusclient.launcher.rerender.Workspace;
import com.nexusclient.launcher.rerender.WorkspaceSharing;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ModRerenderPage – the "Mod Rerender" tab: look up another player by name and see their setup
 * ("workspace") – which mods, resource packs and shaders they use, their mod settings and HUD – and
 * copy any of it. It only works for players who switched on "Let other players render my workspace"
 * (Settings → General, or the My Workspace tab here).
 *
 *   Search        – type a name; their workspace shows with buttons:
 *                     Copy as new instance (everything), or use just their Mods / Config / HUD layout
 *                     in your selected instance (what's replaced is backed up first)
 *   My Workspace  – the sharing switch, which instance to share, and what others will see
 */
public class ModRerenderPage extends VBox {

    private final InstanceStore store;
    private final InstancePanel panel;
    private final VBox body = new VBox(14);
    private final TabStrip tabs;
    private final TextField search = new TextField();
    private JsonObject shown;      // the workspace being looked at
    private String shownName = "";

    public ModRerenderPage(InstanceStore store, InstancePanel panel) {
        this.store = store;
        this.panel = panel;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.MOD_RERENDER.title());
        title.getStyleClass().add("page-title");
        search.setPromptText("Search a player's name");
        search.setPrefWidth(240);
        search.setOnAction(event -> lookUp());
        Button go = new Button("Render");
        go.getStyleClass().add("primary-button");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(event -> lookUp());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, PageHeader.block(Page.MOD_RERENDER, title), spacer, search, go);
        header.setAlignment(Pos.CENTER_LEFT);

        tabs = new TabStrip(Page.MOD_RERENDER.tabs(), this::showTab);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, tabs, scroll);
        showTab("Search");
    }

    /** Shows one sub-tab (public so the developer tour can visit it). */
    public void showTab(String tab) {
        tabs.select(tab);
        if (tab.equals("My Workspace")) {
            body.getChildren().setAll(card(new VBox(12, heading("Share my workspace"), sharingControls(store),
                    hint("Others see your mods, packs and shaders as Modrinth links (never the files), your mod "
                            + "settings and your Nexus HUD. Never your worlds, waypoints, servers, or anything that "
                            + "looks like a password or key."))));
        } else if (shown != null) {
            showWorkspace();
        } else {
            body.getChildren().setAll(People.empty(Icons.RERENDER, "Render someone's setup",
                    "Search a player's name. They need \"Let other players render my workspace\" switched on."));
        }
    }

    /** Looks up the name in the search box (public so the tour can show a result). */
    public void lookUp(String name) {
        search.setText(name);
        lookUp();
    }

    private void lookUp() {
        String name = search.getText().trim();
        if (name.isEmpty()) {
            return;
        }
        NexusOnline online = NexusOnline.get();
        if (!online.signedIn()) {
            body.getChildren().setAll(People.empty(Icons.RERENDER, "Sign in to Nexus first",
                    "Mod Rerender uses the Nexus server."));
            return;
        }
        tabs.select("Search");
        body.getChildren().setAll(hint("Rendering " + name + "'s workspace…"));
        Background.run("rerender", () -> online.get("/api/workspace/" + URLEncoder.encode(name, StandardCharsets.UTF_8))
                .getAsJsonObject(), result -> {
            shownName = result.get("name").getAsString();
            shown = JsonParser.parseString(result.get("data").getAsString()).getAsJsonObject();
            shownUuid = result.get("uuid").getAsString();
            shownUpdated = result.get("updatedAt").getAsString();
            showWorkspace();
        }, error -> body.getChildren().setAll(People.empty(Icons.RERENDER, "Nothing to render", error.getMessage())));
    }

    private String shownUuid = "";
    private String shownUpdated = "";

    // ------------------------------------------------------------------ their workspace

    private void showWorkspace() {
        JsonObject info = Workspace.info(shown);
        List<JsonObject> mods = Workspace.content(shown, ContentType.MOD);
        List<JsonObject> packs = Workspace.content(shown, ContentType.RESOURCE_PACK);
        List<JsonObject> shaders = Workspace.content(shown, ContentType.SHADER);
        int files = Workspace.files(shown).size();
        String version = str(info, "gameVersion");
        String loader = str(info, "loader");

        // --- Who and what ---
        Label name = new Label(shownName);
        name.getStyleClass().add("section-title");
        Label detail = new Label("\"" + str(info, "name") + "\"  ·  Minecraft " + version + "  ·  "
                + capitalise(loader) + "  ·  updated " + shownUpdated.substring(0, Math.min(10, shownUpdated.length())));
        detail.getStyleClass().add("mod-meta");
        VBox who = new VBox(4, name, detail, People.statChips(mods.size() + " mods", packs.size() + " packs",
                shaders.size() + " shaders", files + " setting files", Workspace.hasHud(shown) ? "Nexus HUD" : "No HUD"));
        HBox top = new HBox(14, SkinHeads.forUuid(shownUuid, 56), who);
        top.setAlignment(Pos.CENTER_LEFT);

        // --- What you can do with it ---
        Instance target = panel.selectedInstanceProperty().get();
        String into = target == null ? "your selected instance" : "\"" + target.name() + "\"";
        Button copyAll = new Button("Copy as new instance");
        copyAll.getStyleClass().add("primary-button");
        copyAll.setOnAction(event -> copyAsNewInstance());
        Button useMods = action("Use mods", () -> useInSelected("mods"));
        Button useConfig = action("Use config", () -> useInSelected("config"));
        Button useHud = action("Use HUD layout", () -> useInSelected("hud"));
        useHud.setDisable(!Workspace.hasHud(shown));
        FlowPane actions = new FlowPane(10, 10, copyAll, useMods, useConfig, useHud);
        Label actionsNote = hint("\"Use …\" goes into " + into + "; anything replaced is backed up first."
                + (target != null && !version.equals(WorkspaceSharing.gameVersion(target))
                ? " Careful: their mods are for " + version + "." : ""));

        VBox lists = new VBox(10);
        lists.getChildren().add(contentList("Mods", mods));
        if (!packs.isEmpty()) {
            lists.getChildren().add(contentList("Resource packs", packs));
        }
        if (!shaders.isEmpty()) {
            lists.getChildren().add(contentList("Shaders", shaders));
        }
        body.getChildren().setAll(card(new VBox(14, top, actions, actionsNote)), card(lists));
        Animations.pageIn(body);
    }

    private VBox contentList(String title, List<JsonObject> items) {
        VBox list = new VBox(6, heading(title + " (" + items.size() + ")"));
        if (items.isEmpty()) {
            list.getChildren().add(hint("None."));
        }
        for (JsonObject item : items) {
            Label itemName = new Label(str(item, "title"));
            itemName.getStyleClass().add("mod-title");
            boolean own = item.has("own");
            Label meta = new Label(own ? "added by them – not on Modrinth, can't be copied"
                    : str(item, "versionNumber") + (item.has("enabled") && !item.get("enabled").getAsBoolean() ? "  ·  switched off" : ""));
            meta.getStyleClass().add("mod-meta");
            VBox text = new VBox(1, itemName, meta);
            HBox row = new HBox(10, text);
            row.getStyleClass().add("mod-row");
            row.setOpacity(own ? 0.6 : 1);
            list.getChildren().add(row);
        }
        return list;
    }

    // ------------------------------------------------------------------ using it

    /** A new instance with their version, loader, mods/packs/shaders and settings. */
    private void copyAsNewInstance() {
        JsonObject workspace = shown;
        JsonObject info = Workspace.info(workspace);
        String version = str(info, "gameVersion");
        Instance instance = Instance.create(shownName + "'s " + str(info, "name")).withVersion(version)
                .withLoader(str(info, "loader").isEmpty() ? Instance.VANILLA : str(info, "loader"));
        Modal.confirm("Copy " + shownName + "'s setup?", "Makes a new instance \"" + instance.name()
                        + "\" (Minecraft " + version + ") with their mods, packs, shaders and settings, downloaded from Modrinth.",
                "Copy", () -> {
                    Toast.info("Copying " + shownName + "'s setup…");
                    Background.run("rerender-copy", () -> {
                        Workspace.useAllFiles(workspace, instance);
                        int installed = Workspace.useContent(workspace, instance, version, (message, fraction) -> {});
                        store.addExisting(instance);
                        return installed;
                    }, installed -> {
                        panel.select(instance);
                        Toast.info("Made \"" + instance.name() + "\" with " + installed + " downloads.");
                    }, error -> Toast.error("Couldn't copy the setup: " + error.getMessage()));
                });
    }

    /** Their mods, config or HUD into the selected instance (backed up first). */
    private void useInSelected(String what) {
        Instance target = panel.selectedInstanceProperty().get();
        if (target == null) {
            Toast.error("Pick one of your instances on the left first.");
            return;
        }
        JsonObject workspace = shown;
        String label = what.equals("mods") ? "mods" : what.equals("config") ? "mod settings and game settings" : "Nexus HUD layout";
        Modal.confirm("Use " + shownName + "'s " + label + "?", "They go into \"" + target.name()
                        + "\". Anything replaced is backed up in the instance's nexus-backups folder.",
                "Use them", () -> Background.run("rerender-use", () -> switch (what) {
                    case "mods" -> Workspace.useMods(workspace, target, WorkspaceSharing.gameVersion(target),
                            (message, fraction) -> {}) + " mods installed";
                    case "config" -> Workspace.useConfig(workspace, target) + " setting files copied";
                    default -> Workspace.useHud(workspace, target) > 0 ? "HUD layout copied" : "No HUD layout to copy";
                }, done -> Toast.info(done + " into \"" + target.name() + "\"."),
                        error -> Toast.error("Couldn't do that: " + error.getMessage())));
    }

    // ------------------------------------------------------------------ the sharing switch

    /**
     * "Let other players render my workspace" + which instance – used here (My Workspace) and in
     * Settings → General.
     */
    public static Node sharingControls(InstanceStore store) {
        Settings settings = SettingsStore.get();
        CheckBox share = new CheckBox("Let other players render my workspace");
        share.setSelected(settings.shareWorkspace);
        ComboBox<Instance> which = new ComboBox<>();
        which.getItems().setAll(store.instances());
        which.setPromptText("The one I played last");
        which.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Instance item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        which.setButtonCell(which.getCellFactory().call(null));
        for (Instance instance : store.instances()) {
            if (instance.id().equals(settings.sharedInstanceId)) {
                which.setValue(instance);
            }
        }
        which.setDisable(!settings.shareWorkspace);
        Label status = hint(settings.shareWorkspace ? WorkspaceSharing.status() : "Off – nobody can render your workspace.");
        share.setOnAction(event -> {
            settings.shareWorkspace = share.isSelected();
            SettingsStore.save();
            which.setDisable(!share.isSelected());
            status.setText(share.isSelected() ? "Sharing… (updates after each game too)" : "Off – removed from the Nexus server.");
            WorkspaceSharing.syncLater();
        });
        which.setOnAction(event -> {
            settings.sharedInstanceId = which.getValue() == null ? null : which.getValue().id();
            SettingsStore.save();
            WorkspaceSharing.syncLater();
        });
        HBox pick = new HBox(10, hint("Instance to share"), which);
        pick.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10, share, pick, status);
    }

    // ------------------------------------------------------------------ small helpers

    private static Button action(String text, Runnable onClick) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> onClick.run());
        return button;
    }

    private static VBox card(Node content) {
        VBox card = new VBox(content);
        card.getStyleClass().addAll("settings-card", "flip-in");
        return card;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("torcher-title");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("preview-note");
        label.setWrapText(true);
        return label;
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? "Vanilla" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
