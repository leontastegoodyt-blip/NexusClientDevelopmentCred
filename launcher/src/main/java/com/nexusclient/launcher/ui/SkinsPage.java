package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.account.Account;
import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.account.SkinService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SkinsPage – the Skins tab (the project brief: skin changer – upload and switch Minecraft skins from the
 * launcher, with a 3D preview).
 *
 *   My Skins – the skins saved on this computer: preview one, or wear it in Minecraft
 *   Upload   – add a skin picture (PNG) to the library, and optionally wear it straight away
 *   Capes    – wear one of the capes the account owns, or hide the cape
 *
 * Wearing a skin or cape changes it in Minecraft itself, so it needs a signed-in account.
 */
public class SkinsPage extends HBox {

    private final AccountManager accounts;
    private final SkinModel3D preview = new SkinModel3D(300, 420);
    private final Label previewName = new Label("Default");
    private final VBox body = new VBox(14);
    private final TabStrip tabs;

    // The skin shown in the preview right now.
    private Path previewFile;
    private boolean previewSlim;

    public SkinsPage(AccountManager accounts) {
        this.accounts = accounts;
        getStyleClass().add("tab-page");
        setSpacing(24);

        // --- Left: the 3D preview ---
        previewName.getStyleClass().add("mod-title");
        Label hint = new Label("Drag to spin");
        hint.getStyleClass().add("preview-note");
        VBox left = new VBox(8, preview, previewName, hint);
        left.setAlignment(Pos.TOP_CENTER);
        left.getStyleClass().add("settings-card");
        left.setMinWidth(340);
        preview.setSkin(defaultSkin(), false);

        // --- Right: title, sub-tabs, content ---
        Label title = new Label(Page.SKINS.title());
        title.getStyleClass().add("page-title");
        tabs = new TabStrip(Page.SKINS.tabs(), this::showTab);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox right = new VBox(18, PageHeader.block(Page.SKINS, title), tabs, scroll);
        HBox.setHgrow(right, Priority.ALWAYS);

        getChildren().addAll(right, left);
        showTab("My Skins");
    }

    /** Shows one sub-tab (public so the developer tour can visit it). */
    public void showTab(String tab) {
        tabs.select(tab);
        switch (tab) {
            case "Upload" -> showUpload();
            case "Capes" -> showCapes();
            case "Nexus Cosmetics" -> showCosmetics();
            default -> showLibrary();
        }
    }

    // ================================================================== My Skins

    private void showLibrary() {
        Background.run("skin-library", SkinService::library, skins -> {
            TilePane grid = new TilePane(12, 12);
            grid.setPrefTileWidth(120);
            grid.setMaxWidth(Double.MAX_VALUE);
            for (SkinService.SavedSkin skin : skins) {
                grid.getChildren().add(libraryTile(skin));
            }
            VBox content = new VBox(12);
            if (!signedIn()) {
                content.getChildren().add(note("Sign in (top-right) to wear skins in Minecraft. You can still save "
                        + "and preview skins here without signing in."));
            }
            if (skins.isEmpty()) {
                content.getChildren().add(note("Your skin library is empty. Add a skin in the Upload tab."));
            } else {
                content.getChildren().add(grid);
            }
            body.getChildren().setAll(content);
        }, null);
    }

    private Node libraryTile(SkinService.SavedSkin skin) {
        Image image = load(skin.file());
        ImageView head = new ImageView(image == null ? null : SkinHeads.cutHead(image, 64));
        Label name = new Label(skin.name() + (skin.slim() ? " (slim)" : ""));
        name.getStyleClass().add("mod-meta");
        Button wear = new Button("Wear");
        wear.getStyleClass().add("primary-button");
        wear.setDisable(!signedIn());
        wear.setOnAction(event -> wear(skin.file(), skin.slim()));
        Button delete = new Button("✕");
        delete.getStyleClass().add("secondary-button");
        delete.setOnAction(event -> Modal.confirm("Delete \"" + skin.name() + "\" from your library?",
                "This only removes it from Nexus – it doesn't change your skin in Minecraft.", "Delete",
                () -> Background.run("delete-skin", () -> {
                    SkinService.delete(skin);
                    return null;
                }, done -> showLibrary(), null)));
        VBox tile = new VBox(8, head, name, new HBox(6, wear, delete));
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("mod-row");
        tile.setOnMouseClicked(event -> showPreview(skin.file(), skin.slim(), skin.name()));
        tile.setStyle("-fx-cursor: hand;");
        return tile;
    }

    // ================================================================== Upload

    private void showUpload() {
        Label title = new Label("Add a skin");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("Choose a Minecraft skin picture (a 64 x 64 PNG file – you can make or download them on "
                + "skin websites). It's saved in your library so you can switch back to it any time.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);

        TextField name = new TextField();
        name.setPromptText("Name, e.g. PvP skin");
        ToggleGroup model = new ToggleGroup();
        RadioButton classic = new RadioButton("Classic arms (Steve)");
        classic.setToggleGroup(model);
        classic.setSelected(true);
        RadioButton slim = new RadioButton("Slim arms (Alex)");
        slim.setToggleGroup(model);
        classic.setOnAction(event -> refreshPreviewModel(false));
        slim.setOnAction(event -> refreshPreviewModel(true));

        Label chosen = new Label("No file chosen");
        chosen.getStyleClass().add("mod-meta");
        Button choose = new Button("Choose skin file…");
        choose.getStyleClass().add("secondary-button");
        choose.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a skin");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Skin pictures", "*.png"));
            File file = chooser.showOpenDialog(getScene().getWindow());
            if (file == null) {
                return;
            }
            Image image = load(file.toPath());
            if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                Toast.error("That picture isn't a Minecraft skin – skins are 64 x 64 (or 64 x 32) pixels.");
                return;
            }
            chosen.setText(file.getName());
            if (name.getText().isBlank()) {
                name.setText(file.getName().replaceAll("\\.png$", ""));
            }
            showPreview(file.toPath(), slim.isSelected(), name.getText());
        });

        Button save = new Button("Save to library");
        save.getStyleClass().add("secondary-button");
        save.setOnAction(event -> saveUpload(name.getText(), slim.isSelected(), false));
        Button saveAndWear = new Button("Save & wear in Minecraft");
        saveAndWear.getStyleClass().add("primary-button");
        saveAndWear.setDisable(!signedIn());
        saveAndWear.setOnAction(event -> saveUpload(name.getText(), slim.isSelected(), true));

        VBox card = new VBox(12, title, text, new HBox(10, choose, chosen), name, new HBox(18, classic, slim),
                new HBox(10, save, saveAndWear));
        card.getStyleClass().add("settings-card");
        body.getChildren().setAll(card);
    }

    private void saveUpload(String name, boolean slim, boolean wearToo) {
        if (previewFile == null) {
            Toast.error("Choose a skin file first.");
            return;
        }
        Path file = previewFile;
        Background.run("save-skin", () -> SkinService.save(file, name, slim), saved -> {
            Toast.info("Saved \"" + saved.name() + "\" to your library.");
            if (wearToo) {
                wear(saved.file(), slim);
            }
        }, null);
    }

    // ================================================================== Capes

    /**
     * Nexus cosmetics (capes, wings, hats, emotes) from the Nexus online server. Only other Nexus players
     * see them. Capes show in-game now; wings, hats and emotes are coming. Everything is free for now.
     */
    private void showCosmetics() {
        com.nexusclient.launcher.online.NexusOnline online = com.nexusclient.launcher.online.NexusOnline.get();
        body.getChildren().setAll(note("Loading Nexus cosmetics…"));
        Background.run("cosmetics", () -> {
            online.signIn(accounts);
            return online.get("/api/cosmetics").getAsJsonArray();
        }, items -> {
            VBox list = new VBox(8);
            list.getChildren().add(note("Nexus cosmetics are only visible to other Nexus players. Capes show in-game "
                    + "now; wings, hats and emotes are coming soon. Everything is free for now."));
            for (com.google.gson.JsonElement e : items) {
                com.google.gson.JsonObject item = e.getAsJsonObject();
                String id = item.get("id").getAsString();
                String type = item.get("type").getAsString();
                boolean equipped = item.get("equipped").getAsBoolean();
                javafx.scene.layout.StackPane picture = new javafx.scene.layout.StackPane();
                picture.setMinSize(48, 48);
                picture.setPrefSize(48, 48);
                WebImages.load(online.baseUrl() + item.get("image").getAsString(), image -> {
                    javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
                    view.setFitWidth(48);
                    view.setFitHeight(48);
                    view.setPreserveRatio(true);
                    view.setSmooth(false); // keep pixel art sharp
                    picture.getChildren().add(view);
                });
                Label name = new Label(item.get("name").getAsString());
                name.getStyleClass().add("mod-title");
                Label kind = new Label(type.charAt(0) + type.substring(1).toLowerCase() + (equipped ? " · wearing" : ""));
                kind.getStyleClass().add("mod-meta");
                VBox text = new VBox(2, name, kind);
                HBox.setHgrow(text, Priority.ALWAYS);
                text.setMaxWidth(Double.MAX_VALUE);
                javafx.scene.control.Button button = new javafx.scene.control.Button(equipped ? "Take off" : "Wear");
                button.getStyleClass().add(equipped ? "secondary-button" : "primary-button");
                button.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                button.setOnAction(ev -> Background.run("equip", () -> online.post("/api/cosmetics/equip",
                        java.util.Map.of("type", type, "id", equipped ? "" : id)), ok -> showCosmetics(),
                        error -> Toast.error(error.getMessage())));
                HBox row = new HBox(12, picture, text, button);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getStyleClass().add("mod-row");
                list.getChildren().add(row);
            }
            body.getChildren().setAll(list);
        }, error -> body.getChildren().setAll(note("Can't load Nexus cosmetics: " + error.getMessage())));
    }

    private void showCapes() {
        if (!signedIn()) {
            body.getChildren().setAll(note("Sign in (top-right) to see and wear your capes."));
            return;
        }
        body.getChildren().setAll(note("Loading your capes…"));
        Background.run("capes", () -> SkinService.profile(freshAccount()), profile -> {
            VBox list = new VBox(8);
            for (SkinService.Cape cape : profile.capes()) {
                Label name = new Label(cape.alias());
                name.getStyleClass().add("mod-title");
                HBox.setHgrow(name, Priority.ALWAYS);
                name.setMaxWidth(Double.MAX_VALUE);
                Button wear = new Button(cape.active() ? "Wearing" : "Wear");
                wear.getStyleClass().add(cape.active() ? "secondary-button" : "primary-button");
                wear.setDisable(cape.active());
                wear.setOnAction(event -> setCape(cape.id()));
                HBox row = new HBox(12, name, wear);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("mod-row");
                list.getChildren().add(row);
            }
            if (profile.capes().isEmpty()) {
                list.getChildren().add(note("This account doesn't own any capes."));
            } else {
                Button hide = new Button("Hide my cape");
                hide.getStyleClass().add("secondary-button");
                hide.setOnAction(event -> setCape(null));
                list.getChildren().add(hide);
            }
            body.getChildren().setAll(list);
        }, error -> body.getChildren().setAll(note(error.getMessage())));
    }

    private void setCape(String capeId) {
        Background.run("set-cape", () -> {
            SkinService.setCape(freshAccount(), capeId);
            return null;
        }, done -> {
            Toast.info(capeId == null ? "Cape hidden." : "Cape changed – it shows in-game within a minute.");
            showCapes();
        }, null);
    }

    // ================================================================== wearing a skin

    /** Changes the player's skin in Minecraft itself. */
    private void wear(Path file, boolean slim) {
        if (!signedIn()) {
            Toast.error("Sign in first (top-right) to change your skin in Minecraft.");
            return;
        }
        Toast.info("Changing your skin…");
        Background.run("wear-skin", () -> {
            SkinService.upload(freshAccount(), file, slim);
            return null;
        }, done -> Toast.info("Skin changed! It shows in-game (and on servers) within a minute."), null);
    }

    private boolean signedIn() {
        return accounts.getActive() != null;
    }

    /** A fresh Minecraft token for the active account (runs in the background). */
    private Account freshAccount() throws Exception {
        return accounts.accountForLaunch(accounts.getActive());
    }

    // ================================================================== preview

    private void showPreview(Path file, boolean slim, String name) {
        previewFile = file;
        previewSlim = slim;
        previewName.setText(name == null || name.isBlank() ? file.getFileName().toString() : name);
        preview.setSkin(load(file), slim);
    }

    private void refreshPreviewModel(boolean slim) {
        previewSlim = slim;
        if (previewFile != null) {
            preview.setSkin(load(previewFile), slim);
        }
    }

    private static Image load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new Image(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** The "Nexus default" character (see DefaultSkin – Nexus mustn't include Minecraft's own Steve skin). */
    private static Image defaultSkin() {
        return DefaultSkin.create();
    }

    private static Node note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("torcher-text");
        label.setWrapText(true);
        return label;
    }
}
