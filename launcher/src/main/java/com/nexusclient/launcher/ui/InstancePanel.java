package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.minecraft.VersionManifest;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * InstancePanel – the "INSTANCES" column next to the tab bar.
 *
 * It shows the player's saved instances (from InstanceStore) with a search box on top. Clicking one
 * selects it, and the Home page's Launch bar shows whichever is selected – that's also where the
 * player picks the instance's version. The + button opens a small form to make a new instance.
 *
 * It also downloads Mojang's version list when the launcher opens, which the version dropdown
 * and the "Latest" choice need.
 */
public class InstancePanel extends VBox {

    private final InstanceStore store;
    private final InstanceActions actions;

    // The list on screen shows a filtered view of the saved instances (matching the search box).
    private final FilteredList<Instance> visibleInstances;
    private final ListView<Instance> list;

    private final TextField searchField = new TextField();
    private final Button addButton = new Button();

    // The "new instance" form, hidden until + is clicked.
    private final VBox newForm = new VBox();
    private final TextField nameField = new TextField();

    // Shown under the search box only if the version list couldn't be downloaded.
    private final Label versionError = new Label();
    private final Hyperlink retryLink = new Hyperlink("Try again");
    private final VBox errorBox = new VBox(4, versionError, retryLink);

    // Every Minecraft version Nexus supports, once downloaded. Newest first.
    private final ObservableList<MinecraftVersion> versions = FXCollections.observableArrayList();

    // The newest Minecraft release's name, e.g. "26.3" ("…" until loaded).
    private final ReadOnlyStringWrapper latestVersion = new ReadOnlyStringWrapper("…");

    public InstancePanel(InstanceStore store) {
        this.store = store;
        this.actions = new InstanceActions(store, this);
        this.visibleInstances = new FilteredList<>(store.instances());
        this.list = new ListView<>(visibleInstances);

        getStyleClass().add("instance-panel");
        getChildren().addAll(buildHeader(), buildSearch(), buildErrorBox(), buildNewForm(), buildList());
        list.getSelectionModel().selectFirst();
        loadVersions();
    }

    /**
     * The instance that's currently selected (or null if none).
     * A "property" is a value other parts of the program can listen to, so they get told
     * whenever it changes. The Home page listens to this one.
     */
    public ReadOnlyObjectProperty<Instance> selectedInstanceProperty() {
        return list.getSelectionModel().selectedItemProperty();
    }

    /** Opens the settings pop-up for an instance (also used by the gear button next to LAUNCH). */
    public void openSettings(Instance instance) {
        if (instance != null) {
            Modal.show(new InstanceSettingsPane(instance, store, this, actions), 660);
        }
    }

    /** The shared instance actions (duplicate, export, delete…). */
    public InstanceActions actions() {
        return actions;
    }

    /** Selects an instance in the list. */
    public void select(Instance instance) {
        list.getSelectionModel().select(instance);
    }

    /** All of the player's instances (not just the ones matching the search). */
    public ObservableList<Instance> getInstances() {
        return store.instances();
    }

    /** The Minecraft versions players can pick from, newest first (empty until downloaded). */
    public ObservableList<MinecraftVersion> getVersions() {
        return versions;
    }

    /** The newest Minecraft release, e.g. "26.3". */
    public ReadOnlyStringProperty latestVersionProperty() {
        return latestVersion.getReadOnlyProperty();
    }

    /**
     * Works out which actual Minecraft version an instance plays: its chosen version, or for
     * "Latest", the newest release right now. Returns null if the instance has no version yet or
     * the version list hasn't downloaded.
     */
    public MinecraftVersion resolveVersion(Instance instance) {
        if (instance == null || !instance.hasVersion() || versions.isEmpty()) {
            return null;
        }
        if (Instance.LATEST.equals(instance.version())) {
            return versions.get(0);
        }
        for (MinecraftVersion version : versions) {
            if (version.id().equals(instance.version())) {
                return version;
            }
        }
        return null;
    }

    /** How to show a version choice in text: "Latest (26.3)", "1.8.9", or "No version yet". */
    public String describeVersion(String choice) {
        if (choice == null) {
            return "No version yet";
        }
        if (Instance.LATEST.equals(choice)) {
            return versions.isEmpty() ? "Latest" : "Latest (" + versions.get(0).id() + ")";
        }
        return choice;
    }

    // ------------------------------------------------------------------ building the panel

    /** "INSTANCES 3" title with the + button on the right. */
    private HBox buildHeader() {
        Label title = new Label("INSTANCES");
        title.getStyleClass().add("panel-title");

        // A small number that always shows how many instances there are.
        Label count = new Label();
        count.getStyleClass().add("count-pill");
        count.textProperty().bind(Bindings.size(store.instances()).asString());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        addButton.setGraphic(Icons.create(Icons.ADD, 18));
        addButton.getStyleClass().add("icon-button");
        addButton.setTooltip(new Tooltip("New or import"));
        addButton.setOnAction(event -> addMenu().show(addButton, Side.BOTTOM, 0, 4));

        HBox header = new HBox(8, title, count, spacer, addButton);
        header.getStyleClass().add("panel-header");
        return header;
    }

    /** The menu under the + button: make a new instance, or import one. */
    private ContextMenu addMenu() {
        MenuItem create = new MenuItem("New instance");
        create.setOnAction(event -> showNewForm());
        MenuItem importFile = new MenuItem("Import from file…");
        importFile.setOnAction(event -> actions.importFile(getScene().getWindow()));
        MenuItem pasteCode = new MenuItem("Paste setup code…");
        pasteCode.setOnAction(event -> actions.pasteSetupCode());
        return new ContextMenu(create, importFile, pasteCode);
    }

    /** The right-click menu for one instance. */
    private ContextMenu instanceMenu(Instance instance) {
        MenuItem settings = new MenuItem("Settings…");
        settings.setOnAction(event -> openSettings(instance));
        MenuItem folder = new MenuItem("Open folder");
        folder.setOnAction(event -> actions.openFolder(instance));
        MenuItem duplicate = new MenuItem("Duplicate");
        duplicate.setOnAction(event -> actions.duplicate(instance));
        MenuItem export = new MenuItem("Export…");
        export.setOnAction(event -> actions.export(instance, getScene().getWindow()));
        MenuItem code = new MenuItem("Copy setup code");
        code.setOnAction(event -> actions.copySetupCode(instance));
        MenuItem delete = new MenuItem("Delete…");
        delete.setOnAction(event -> actions.confirmDelete(instance));
        return new ContextMenu(settings, folder, duplicate, export, code, new SeparatorMenuItem(), delete);
    }

    /** The search box. Typing in it hides instances whose name or version doesn't match. */
    private StackPane buildSearch() {
        searchField.setPromptText("Search instances");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((observable, oldText, newText) -> {
            String query = newText.trim().toLowerCase();
            visibleInstances.setPredicate(instance -> query.isEmpty()
                    || instance.name().toLowerCase().contains(query)
                    || describeVersion(instance.version()).toLowerCase().contains(query));
        });

        // Put a magnifying-glass icon on top of the box, at its left end.
        Node icon = Icons.create(Icons.SEARCH, 16);
        icon.getStyleClass().add("search-icon");
        icon.setMouseTransparent(true); // clicks go through the icon to the box underneath
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        StackPane.setMargin(icon, new Insets(0, 0, 0, 10));

        StackPane search = new StackPane(searchField, icon);
        VBox.setMargin(search, new Insets(0, 12, 10, 12));
        return search;
    }

    /** A small message + "Try again" link, shown only if the version list couldn't be downloaded. */
    private VBox buildErrorBox() {
        versionError.getStyleClass().add("list-placeholder");
        versionError.setWrapText(true);
        retryLink.getStyleClass().add("retry-link");
        retryLink.setOnAction(event -> loadVersions());
        errorBox.setPadding(new Insets(0, 16, 10, 20));
        errorBox.managedProperty().bind(errorBox.visibleProperty()); // take no space when hidden
        errorBox.setVisible(false);
        return errorBox;
    }

    /** The small form for making a new instance: just a name. The version is picked afterwards. */
    private VBox buildNewForm() {
        Label formTitle = new Label("New instance");
        formTitle.getStyleClass().add("form-title");

        nameField.setPromptText("Name, e.g. Bedwars");
        nameField.setOnAction(event -> createInstance()); // pressing Enter also creates it

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> setFormVisible(false));

        Button create = new Button("Create");
        create.getStyleClass().add("primary-button");
        create.setOnAction(event -> createInstance());

        HBox buttons = new HBox(8, cancel, create);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        newForm.getChildren().addAll(formTitle, nameField, buttons);
        newForm.getStyleClass().add("new-instance-form");
        VBox.setMargin(newForm, new Insets(0, 12, 10, 12));
        setFormVisible(false);
        return newForm;
    }

    /** The list of instances itself. */
    private ListView<Instance> buildList() {
        list.getStyleClass().add("instance-list");
        list.setCellFactory(listView -> new InstanceCell()); // how each row looks: see InstanceCell below

        Label placeholder = new Label("No instances match your search.");
        placeholder.getStyleClass().add("list-placeholder");
        list.setPlaceholder(placeholder);

        VBox.setVgrow(list, Priority.ALWAYS); // the list takes all the height that's left
        return list;
    }

    // ------------------------------------------------------------------ loading versions

    /**
     * Downloads the version list from Mojang in the background.
     *
     * Downloading takes a moment. Doing it on the "UI thread" (the part of the program that draws
     * the window) would freeze the window, so it runs as a background Task on another thread.
     * JavaFX calls setOnSucceeded / setOnFailed back on the UI thread when it's done.
     */
    private void loadVersions() {
        errorBox.setVisible(false);

        Task<List<MinecraftVersion>> task = new Task<>() {
            @Override
            protected List<MinecraftVersion> call() throws Exception {
                return VersionManifest.fetchSupportedVersions();
            }
        };

        task.setOnSucceeded(event -> {
            versions.setAll(task.getValue());
            if (!versions.isEmpty()) {
                latestVersion.set(versions.get(0).id()); // first = newest release
            }
            list.refresh(); // redraw the rows so "Latest" shows its real version number
        });

        task.setOnFailed(event -> {
            versionError.setText("Couldn't get the Minecraft version list from Mojang. "
                    + "Check your internet connection.");
            errorBox.setVisible(true);
            task.getException().printStackTrace(); // technical details go to the terminal
        });

        Thread thread = new Thread(task, "version-loader");
        thread.setDaemon(true); // don't keep the app running after the window is closed
        thread.start();
    }

    // ------------------------------------------------------------------ the new-instance form

    private void showNewForm() {
        nameField.clear();
        setFormVisible(true);
        nameField.requestFocus(); // put the typing cursor in the name box
    }

    private void createInstance() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "Instance " + (store.instances().size() + 1); // a sensible name if left blank
        }
        searchField.clear(); // make sure the new instance isn't hidden by an old search
        Instance instance = store.add(name);
        list.getSelectionModel().select(instance); // the Launch bar will ask for its version
        setFormVisible(false);
    }

    /** Shows or hides the form. "managed" = false also stops it taking up space when hidden. */
    private void setFormVisible(boolean visible) {
        newForm.setVisible(visible);
        newForm.setManaged(visible);
    }

    // ------------------------------------------------------------------ how one row looks

    /**
     * InstanceCell – draws one instance in the list: its coloured badge, then the name, and
     * the chosen version and mod loader underneath.
     *
     * ListView re-uses a few cell objects while you scroll and calls updateItem() to fill each one
     * with the right instance (or with nothing, for empty rows).
     */
    private class InstanceCell extends ListCell<Instance> {
        @Override
        protected void updateItem(Instance instance, boolean empty) {
            super.updateItem(instance, empty);
            setText(null);
            if (empty || instance == null) {
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            setContextMenu(instanceMenu(instance)); // right-click menu

            Label name = new Label(instance.name());
            name.getStyleClass().add("instance-name");

            Label details;
            if (instance.hasVersion()) {
                // The mod loader: "Vanilla" = plain Minecraft, or "Fabric".
                details = new Label(describeVersion(instance.version()) + "  ·  " + instance.loaderLabel());
                details.getStyleClass().add("instance-version");
            } else {
                details = new Label("Choose a version");
                details.getStyleClass().add("instance-version-missing");
            }

            VBox text = new VBox(2, name, details);
            HBox row = new HBox(12, InstanceBadge.create(instance, 38), text);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }
}
