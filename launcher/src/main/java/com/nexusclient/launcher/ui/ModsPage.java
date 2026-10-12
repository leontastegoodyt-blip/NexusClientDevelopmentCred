package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.mods.ContentType;
import com.nexusclient.launcher.mods.ModManager;
import com.nexusclient.launcher.mods.ModrinthApi;
import com.nexusclient.launcher.mods.MrpackImporter;
import com.nexusclient.launcher.game.Downloader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ModsPage – the Mods tab (the project brief: Mods, packs and shaders).
 *
 *   Mods / Resource Packs / Shaders / Modpacks – search Modrinth, filtered to what works with the
 *                                                selected instance, and install in one click
 *   Installed – what the selected instance has: switch on/off, reorder packs, shaders on/off,
 *               updates, remove, and add your own files
 *
 * Everything applies to the instance selected on the left.
 */
public class ModsPage extends VBox {

    private static final int PAGE_SIZE = 20;

    private final InstanceStore store;
    private final InstancePanel panel;

    private final Label target = new Label();
    private final VBox body = new VBox(14);
    private TabStrip tabs;
    private String currentTab = "Mods";

    // Browsing state.
    private final TextField searchField = new TextField();
    private final ComboBox<String> sortBox = new ComboBox<>();
    private final VBox results = new VBox(10);
    private final Button loadMore = new Button("Load more");
    private int offset = 0;

    // Updates found by the last check: file name → newer version.
    private final Map<String, ModrinthApi.Version> updates = new HashMap<>();

    public ModsPage(InstanceStore store, InstancePanel panel) {
        this.store = store;
        this.panel = panel;
        getStyleClass().add("tab-page");

        Label title = new Label(Page.MODS.title());
        title.getStyleClass().add("page-title");
        target.getStyleClass().add("step-pill");
        target.setMinWidth(Region.USE_PREF_SIZE); // always show the whole "For: …" label
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(PageHeader.block(Page.MODS, title), spacer, target);
        header.setAlignment(Pos.CENTER_LEFT);

        tabs = new TabStrip(Page.MODS.tabs(), this::showTab);

        searchField.setPromptText("Search Modrinth…");
        searchField.getStyleClass().add("search-field");
        searchField.setOnAction(event -> search(true));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        sortBox.getItems().setAll("Relevance", "Most downloaded", "Recently updated", "Newest");
        sortBox.setValue("Relevance");
        sortBox.getStyleClass().add("form-combo");
        sortBox.setOnAction(event -> search(true));
        loadMore.getStyleClass().add("secondary-button");
        loadMore.setOnAction(event -> search(false));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, tabs, scroll);

        // When a different instance is selected, show things for that one instead.
        panel.selectedInstanceProperty().addListener((observable, oldInstance, newInstance) -> showTab(currentTab));
        showTab(currentTab);
    }

    // ================================================================== which instance

    /** The selected instance and its actual Minecraft version, or null (with the target label explaining). */
    private ModManager manager() {
        Instance instance = panel.selectedInstanceProperty().get();
        if (instance == null) {
            target.setText("No instance selected");
            return null;
        }
        MinecraftVersion version = panel.resolveVersion(instance);
        if (version == null) {
            target.setText(instance.name() + "  ·  choose a version first");
            return null;
        }
        target.setText("For: " + instance.name() + "  ·  " + version.id() + "  ·  " + instance.loaderLabel());
        return new ModManager(instance, version.id());
    }

    /** Shows one sub-tab (public so the developer tour can visit it). */
    public void showTab(String tab) {
        if (tabs != null) {
            tabs.select(tab); // keep the highlighted pill in step
        }
        currentTab = tab;
        if (tab.equals("Installed")) {
            showInstalled();
        } else {
            showBrowse();
        }
    }

    private ContentType browseType() {
        return switch (currentTab) {
            case "Resource Packs" -> ContentType.RESOURCE_PACK;
            case "Shaders" -> ContentType.SHADER;
            case "Modpacks" -> ContentType.MODPACK;
            default -> ContentType.MOD;
        };
    }

    // ================================================================== browse Modrinth

    private void showBrowse() {
        ModManager manager = manager();
        ContentType type = browseType();
        HBox searchRow = new HBox(10, searchField, sortBox);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        body.getChildren().setAll(searchRow);

        if (type != ContentType.MODPACK) {
            if (manager == null) {
                body.getChildren().add(People.empty(Icons.MODS, "Pick an instance first", null));
                return;
            }
            String reason = manager.whyNot(type);
            if (reason != null) {
                Instance instance = panel.selectedInstanceProperty().get();
                Button fix = null;
                if (type == ContentType.MOD || type == ContentType.SHADER) {
                    fix = new Button("Switch " + instance.name() + " to Fabric");
                    fix.getStyleClass().add("primary-button");
                    fix.setOnAction(event -> {
                        panel.select(store.update(instance.withLoader(Instance.FABRIC)));
                        Toast.info(instance.name() + " now uses Fabric.");
                    });
                }
                body.getChildren().add(notice(reason, fix));
                return;
            }
        } else {
            body.getChildren().add(notice("Installing a modpack makes a new instance with everything in it. "
                    + "Nexus supports Fabric modpacks.", null));
        }
        body.getChildren().addAll(results, loadMore);
        search(true);
    }

    /** Asks Modrinth for results (fresh = a new search, otherwise the next page). */
    private void search(boolean fresh) {
        if (currentTab.equals("Installed")) {
            return;
        }
        ContentType type = browseType();
        ModManager manager = type == ContentType.MODPACK ? null : manager();
        if (type != ContentType.MODPACK && (manager == null || manager.whyNot(type) != null)) {
            return;
        }
        if (fresh) {
            offset = 0;
            results.getChildren().setAll(loadingLabel("Searching Modrinth…"));
        }
        String sort = switch (sortBox.getValue()) {
            case "Most downloaded" -> "downloads";
            case "Recently updated" -> "updated";
            case "Newest" -> "newest";
            default -> "relevance";
        };
        String gameVersion = manager == null ? null : manager_gameVersion();
        String loader = switch (type) {
            case MOD -> manager.modLoader();
            case SHADER -> "iris";
            case MODPACK -> "fabric";
            default -> null;
        };
        String query = searchField.getText();
        int from = offset;
        Background.run("modrinth-search",
                () -> ModrinthApi.search(query, type.modrinthType(), gameVersion, loader, sort, from, PAGE_SIZE),
                result -> {
                    if (fresh) {
                        results.getChildren().clear();
                    }
                    Set<String> installed = manager == null ? Set.of() : manager.installedProjectIds();
                    for (ModrinthApi.Hit hit : result.hits()) {
                        results.getChildren().add(resultRow(hit, type, installed.contains(hit.project_id())));
                    }
                    if (result.hits().isEmpty() && fresh) {
                        results.getChildren().add(loadingLabel("Nothing found. Try another search."));
                    }
                    offset = from + result.hits().size();
                    loadMore.setVisible(offset < result.total_hits());
                },
                error -> results.getChildren().setAll(loadingLabel(error.getMessage())));
    }

    /** The Minecraft version of the selected instance (only called when manager() isn't null). */
    private String manager_gameVersion() {
        return panel.resolveVersion(panel.selectedInstanceProperty().get()).id();
    }

    /** One search result: icon, name, author, description, downloads, and an Install button. */
    private Node resultRow(ModrinthApi.Hit hit, ContentType type, boolean alreadyInstalled) {
        Label name = new Label(hit.title());
        name.getStyleClass().add("mod-title");
        Label author = new Label("by " + hit.author());
        author.getStyleClass().add("mod-author");
        HBox nameRow = new HBox(8, name, author);
        nameRow.setAlignment(Pos.BASELINE_LEFT);
        Label description = new Label(hit.description());
        description.getStyleClass().add("mod-description");
        description.setWrapText(true);
        description.setMaxHeight(38); // about two lines
        Label meta = new Label(formatCount(hit.downloads()) + " downloads");
        meta.getStyleClass().add("mod-meta");
        VBox text = new VBox(3, nameRow, description, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button install = new Button(alreadyInstalled ? "Installed" : "Install");
        install.getStyleClass().add(alreadyInstalled ? "secondary-button" : "primary-button");
        install.setDisable(alreadyInstalled);
        install.setOnAction(event -> install(hit, type, install));
        install.setMinWidth(Region.USE_PREF_SIZE); // never squash the button
        text.setMinWidth(0);

        HBox row = new HBox(14, icon(hit.icon_url(), 52), text, install);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        return row;
    }

    private void install(ModrinthApi.Hit hit, ContentType type, Button button) {
        button.setDisable(true);
        button.setText("Installing…");
        if (type == ContentType.MODPACK) {
            Background.run("install-modpack", () -> {
                // Modrinth doesn't always apply the loader filter to modpacks, so check each version ourselves.
                // Fabric first (the Nexus mod runs there); Forge packs work too, without the Nexus mod.
                List<ModrinthApi.Version> versions = ModrinthApi.versions(hit.project_id(), List.of("fabric", "forge"), null);
                ModrinthApi.Version packVersion = versions.stream()
                        .filter(v -> v.loaders() != null && v.loaders().contains("fabric"))
                        .findFirst()
                        .or(() -> versions.stream().filter(v -> v.loaders() != null && v.loaders().contains("forge")).findFirst())
                        .orElseThrow(() -> new java.io.IOException("This modpack has no Fabric or Forge version."));
                ModrinthApi.VersionFile file = packVersion.primaryFile();
                Path temp = Files.createTempFile("nexus-modpack", ".mrpack");
                try (Downloader downloader = new Downloader()) {
                    downloader.download(new Downloader.Job(file.url(), temp, file.hashes().get("sha1"), file.size()));
                }
                Instance instance = MrpackImporter.importPack(temp, (message, fraction) -> {});
                Files.deleteIfExists(temp);
                return store.addExisting(instance);
            }, instance -> {
                button.setText("Installed");
                panel.select(instance);
                Toast.info("Installed the modpack \"" + instance.name() + "\" as a new instance.");
            }, error -> {
                button.setDisable(false);
                button.setText("Install");
                Toast.error("Couldn't install " + hit.title() + ": " + error.getMessage());
            });
            return;
        }
        ModManager manager = manager();
        if (manager == null) {
            return;
        }
        Background.run("install-" + hit.slug(), () -> manager.install(hit.project_id(), type, (message, fraction) -> {}),
                title -> {
                    button.setText("Installed");
                    button.getStyleClass().setAll("button", "secondary-button");
                    Toast.info("Installed " + title + (type == ContentType.SHADER
                            ? ". Turn shaders on in the Installed tab." : "."));
                },
                error -> {
                    button.setDisable(false);
                    button.setText("Install");
                    Toast.error("Couldn't install " + hit.title() + ": " + error.getMessage());
                });
    }

    // ================================================================== installed

    private void showInstalled() {
        ModManager manager = manager();
        if (manager == null) {
            body.getChildren().setAll(People.empty(Icons.MODS, "Pick an instance first", null));
            return;
        }
        Button check = new Button("Check for updates");
        check.getStyleClass().add("secondary-button");
        check.setOnAction(event -> checkUpdates(manager));
        Button folder = new Button("Open instance folder");
        folder.getStyleClass().add("secondary-button");
        folder.setOnAction(event -> panel.actions().openFolder(panel.selectedInstanceProperty().get()));
        HBox toolbar = new HBox(10, check, folder);

        body.getChildren().setAll(toolbar, loadingLabel("Loading…"));
        Background.run("list-installed", () -> List.of(
                        manager.whyNot(ContentType.MOD) == null ? manager.list(ContentType.MOD) : List.<ModManager.Installed>of(),
                        manager.list(ContentType.RESOURCE_PACK),
                        manager.whyNot(ContentType.SHADER) == null ? manager.list(ContentType.SHADER) : List.<ModManager.Installed>of()),
                lists -> {
                    body.getChildren().setAll(toolbar);
                    if (manager.whyNot(ContentType.MOD) == null) {
                        body.getChildren().add(section(manager, ContentType.MOD, lists.get(0)));
                    }
                    body.getChildren().add(section(manager, ContentType.RESOURCE_PACK, lists.get(1)));
                    if (manager.whyNot(ContentType.SHADER) == null) {
                        body.getChildren().add(section(manager, ContentType.SHADER, lists.get(2)));
                    }
                }, null);
    }

    /** One section of the Installed tab (mods, resource packs or shaders). */
    private Node section(ModManager manager, ContentType type, List<ModManager.Installed> items) {
        Label title = new Label(type.label() + "  (" + items.size() + ")");
        title.getStyleClass().add("section-title");
        Button add = new Button("Add files…");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> addOwnFiles(manager, type));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer);
        header.setAlignment(Pos.CENTER_LEFT);

        if (type == ContentType.SHADER) {
            // The simple on/off switch for shaders (the project brief).
            CheckBox shadersOn = new CheckBox("Shaders on");
            try {
                shadersOn.setSelected(manager.shadersEnabled());
            } catch (Exception ignored) {
                // no Iris settings yet
            }
            shadersOn.setOnAction(event -> saveShaders(manager, shadersOn.isSelected(), null));
            header.getChildren().add(shadersOn);
        }
        header.getChildren().add(add);

        VBox list = new VBox(6);
        if (items.isEmpty()) {
            list.getChildren().add(loadingLabel(switch (type) {
                case MOD -> "No mods yet – find some in the Mods tab.";
                case RESOURCE_PACK -> "No resource packs yet – find some in the Resource Packs tab.";
                default -> "No shaders yet – find some in the Shaders tab.";
            }));
        }
        for (ModManager.Installed item : items) {
            list.getChildren().add(installedRow(manager, item));
        }
        VBox section = new VBox(10, header, list);
        section.getStyleClass().add("settings-card");
        return section;
    }

    /** One installed thing, with its on/off tick, update button and remove button. */
    private Node installedRow(ModManager manager, ModManager.Installed item) {
        Label name = new Label(item.displayName());
        name.getStyleClass().add("mod-title");
        String version = item.entry() != null && item.entry().versionNumber() != null && !item.entry().versionNumber().isEmpty()
                ? item.entry().versionNumber() : item.fileName();
        Label detail = new Label(version + (item.entry() == null ? "  ·  added by you" : ""));
        detail.getStyleClass().add("mod-meta");
        VBox text = new VBox(2, name, detail);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");

        switch (item.type()) {
            case MOD -> {
                CheckBox enabled = new CheckBox();
                enabled.setSelected(item.enabled());
                enabled.setOnAction(event -> Background.run("toggle-mod", () -> {
                    manager.setModEnabled(item, enabled.isSelected());
                    return null;
                }, done -> showInstalled(), null));
                row.getChildren().add(enabled);
            }
            case RESOURCE_PACK -> {
                CheckBox enabled = new CheckBox();
                enabled.setSelected(item.enabled());
                enabled.setOnAction(event -> Background.run("toggle-pack", () -> {
                    manager.setResourcePackEnabled(item.fileName(), enabled.isSelected());
                    return null;
                }, done -> showInstalled(), null));
                row.getChildren().addAll(enabled, packIcon(manager, item));
            }
            case SHADER -> {
                CheckBox selected = new CheckBox();
                selected.setSelected(item.enabled());
                selected.setOnAction(event -> saveShaders(manager, true, item.fileName()));
                row.getChildren().add(selected);
            }
            default -> { }
        }
        row.getChildren().add(text);

        if (item.type() == ContentType.RESOURCE_PACK && item.enabled()) {
            Button up = smallButton("↑", () -> movePack(manager, item, true));
            Button down = smallButton("↓", () -> movePack(manager, item, false));
            row.getChildren().addAll(up, down);
        }
        ModrinthApi.Version newer = updates.get(item.fileName());
        if (newer != null) {
            Button update = new Button("Update to " + newer.version_number());
            update.getStyleClass().add("primary-button");
            update.setOnAction(event -> {
                update.setDisable(true);
                Background.run("update", () -> {
                    manager.update(item, newer);
                    return null;
                }, done -> {
                    updates.remove(item.fileName());
                    Toast.info("Updated " + item.displayName() + ".");
                    showInstalled();
                }, null);
            });
            row.getChildren().add(update);
        }
        Button remove = new Button("Remove");
        remove.getStyleClass().add("danger-button");
        remove.setOnAction(event -> Modal.confirm("Remove " + item.displayName() + "?",
                "The file is deleted from this instance.", "Remove",
                () -> Background.run("remove", () -> {
                    manager.remove(item);
                    return null;
                }, done -> showInstalled(), null)));
        row.getChildren().add(remove);
        return row;
    }

    private void movePack(ModManager manager, ModManager.Installed item, boolean up) {
        Background.run("move-pack", () -> {
            manager.moveResourcePack(item.fileName(), up);
            return null;
        }, done -> showInstalled(), null);
    }

    private void saveShaders(ModManager manager, boolean on, String pack) {
        Background.run("shaders", () -> {
            manager.setShaders(on, pack);
            return null;
        }, done -> {
            Toast.info(on ? "Shaders on" + (pack != null ? " (" + pack + ")" : "") + "." : "Shaders off.");
            showInstalled();
        }, null);
    }

    private void checkUpdates(ModManager manager) {
        Toast.info("Checking Modrinth for updates…");
        Background.run("check-updates", () -> {
            Map<String, ModrinthApi.Version> found = new HashMap<>();
            for (ContentType type : List.of(ContentType.MOD, ContentType.RESOURCE_PACK, ContentType.SHADER)) {
                found.putAll(manager.checkUpdates(type));
            }
            return found;
        }, found -> {
            updates.clear();
            updates.putAll(found);
            Toast.info(found.isEmpty() ? "Everything is up to date." : found.size() + " update(s) available.");
            showInstalled();
        }, null);
    }

    private void addOwnFiles(ModManager manager, ContentType type) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.nexusclient.launcher.i18n.Translator.tr("Add " + type.label().toLowerCase(Locale.ROOT)));
        chooser.getExtensionFilters().add(type == ContentType.MOD
                ? new FileChooser.ExtensionFilter("Mod files", "*.jar")
                : new FileChooser.ExtensionFilter("Pack files", "*.zip"));
        List<File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        Background.run("add-files", () -> {
            manager.addOwnFiles(files.stream().map(File::toPath).toList(), type);
            return files.size();
        }, count -> {
            Toast.info("Added " + count + " file(s).");
            showInstalled();
        }, null);
    }

    // ================================================================== small pieces

    /** A picture from the internet (loads in the background), with a dark square until it arrives. */
    private static Node icon(String url, double size) {
        Region placeholder = new Region();
        placeholder.getStyleClass().add("head-placeholder");
        StackPane box = new StackPane(placeholder);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.getStyleClass().add("mod-icon");
        if (url != null && !url.isBlank()) {
            // WebImages also reads WebP, which most Modrinth icons use.
            WebImages.load(url, image -> {
                ImageView view = new ImageView(image);
                view.setFitWidth(size);
                view.setFitHeight(size);
                view.setSmooth(true);
                view.setPreserveRatio(true);
                box.getChildren().add(view);
            });
        }
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(size, size);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        box.setClip(clip);
        return box;
    }

    /** A resource pack's own picture (pack.png inside its zip or folder), if it has one. */
    private static Node packIcon(ModManager manager, ModManager.Installed item) {
        StackPane box = (StackPane) icon(null, 36);
        Path file = manager.fileOf(item);
        Background.run("pack-icon", () -> {
            if (Files.isDirectory(file)) {
                Path png = file.resolve("pack.png");
                if (Files.exists(png)) {
                    try (InputStream in = Files.newInputStream(png)) {
                        return new Image(in, 36, 36, true, false);
                    }
                }
                return null;
            }
            try (ZipFile zip = new ZipFile(file.toFile())) {
                ZipEntry entry = zip.getEntry("pack.png");
                if (entry == null) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    return new Image(in, 36, 36, true, false); // false = keep pixel art sharp
                }
            }
        }, image -> {
            if (image != null) {
                ImageView view = new ImageView(image);
                box.getChildren().add(view);
            }
        }, error -> { });
        return box;
    }

    private static Node notice(String message, Button action) {
        Label text = new Label(message);
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);
        VBox notice = new VBox(12, text);
        if (action != null) {
            notice.getChildren().add(action);
        }
        notice.getStyleClass().add("settings-card");
        return notice;
    }

    private static Label loadingLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("preview-note");
        label.setWrapText(true);
        return label;
    }

    private static Button smallButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    /** 1234567 → "1.2M". */
    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format(Locale.ROOT, "%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }
}
