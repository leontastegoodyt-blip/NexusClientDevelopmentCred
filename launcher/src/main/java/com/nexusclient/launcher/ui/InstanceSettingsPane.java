package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.settings.SettingsStore;
import com.nexusclient.launcher.torcherrig.TorcherRig;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * InstanceSettingsPane – the pop-up for one instance's own settings (the gear button next to
 * LAUNCH, or right-click → Settings):
 *
 *   name, Minecraft version, mod loader, its own memory amount, extra Java options,
 *   plus Open folder / Duplicate / Export / Copy setup code / Delete.
 */
public class InstanceSettingsPane extends VBox {

    private final Instance instance;
    private final InstanceStore store;
    private final InstancePanel panel;
    private final InstanceActions actions;

    private final TextField nameField = new TextField();
    private final ComboBox<String> versionBox = new ComboBox<>();
    private final ComboBox<String> loaderBox = new ComboBox<>();
    private final CheckBox ownRam = new CheckBox("Give this instance its own memory amount");
    private final Slider ramSlider;
    private final Label ramValue = new Label();
    private final TextField jvmArgsField = new TextField();

    public InstanceSettingsPane(Instance instance, InstanceStore store, InstancePanel panel, InstanceActions actions) {
        this.instance = instance;
        this.store = store;
        this.panel = panel;
        this.actions = actions;
        setSpacing(16);

        Label title = new Label("Instance settings");
        title.getStyleClass().add("torcher-title");

        // --- Name ---
        nameField.setText(instance.name());

        // --- Version: "Latest" + every supported version ---
        List<String> choices = new ArrayList<>();
        choices.add(Instance.LATEST);
        for (MinecraftVersion version : panel.getVersions()) {
            choices.add(version.id());
        }
        if (instance.version() != null && !choices.contains(instance.version())) {
            choices.add(instance.version()); // keep its current choice even if the list is offline
        }
        versionBox.getItems().setAll(choices);
        versionBox.setValue(instance.version());
        versionBox.setPromptText("Choose a version");
        versionBox.setConverter(converter(panel::describeVersion));
        versionBox.getStyleClass().add("form-combo");
        versionBox.setMaxWidth(Double.MAX_VALUE);

        // --- Mod loader ---
        loaderBox.getItems().setAll(Instance.VANILLA, Instance.FABRIC, Instance.FORGE);
        loaderBox.setValue(instance.loaderOrVanilla());
        loaderBox.setConverter(converter(InstanceSettingsPane::loaderName));
        loaderBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String loader, boolean empty) {
                super.updateItem(loader, empty);
                setText(empty || loader == null ? null : loaderName(loader));
                setDisable(Instance.FORGE.equals(loader)); // not available yet
            }
        });
        loaderBox.getStyleClass().add("form-combo");
        loaderBox.setMaxWidth(Double.MAX_VALUE);
        Label loaderNote = new Label("Fabric loads Fabric mods (added in the Mods tab). Minecraft 1.8.9 uses "
                + "Legacy Fabric. Forge support is coming soon.");
        loaderNote.getStyleClass().add("preview-note");
        loaderNote.setWrapText(true);

        // --- Memory ---
        long totalMb = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize() / (1024 * 1024);
        int maxMb = (int) Math.max(2048, Math.min(totalMb - 2048, 32768));
        int global = SettingsStore.get().ramMb;
        ramSlider = new Slider(1024, maxMb, instance.ramMb() > 0 ? Math.min(instance.ramMb(), maxMb)
                : (global > 0 ? Math.min(global, maxMb) : RamPromptPane.recommendedMb(totalMb)));
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setMinorTickCount(1);
        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ownRam.setSelected(instance.ramMb() > 0);
        ramValue.getStyleClass().add("change-value");
        Runnable updateRam = () -> {
            ramSlider.setDisable(!ownRam.isSelected());
            ramValue.setText(ownRam.isSelected() ? TorcherRig.formatGb(snappedRam())
                    : "Global setting (" + (global > 0 ? TorcherRig.formatGb(global) : "Auto") + ")");
        };
        ownRam.selectedProperty().addListener((observable, was, now) -> updateRam.run());
        ramSlider.valueProperty().addListener((observable, was, now) -> updateRam.run());
        updateRam.run();

        // --- Java options (advanced) ---
        jvmArgsField.setText(instance.jvmArgs() == null ? "" : instance.jvmArgs());
        jvmArgsField.setPromptText("e.g. -XX:+UseZGC   (leave empty unless you know what these do)");

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(10);
        addRow(form, 0, "Name", nameField);
        addRow(form, 1, "Minecraft version", versionBox);
        addRow(form, 2, "Mod loader", new VBox(6, loaderBox, loaderNote));
        addRow(form, 3, "Memory (RAM)", new VBox(6, ownRam, ramSlider, ramValue));
        addRow(form, 4, "Java options", jvmArgsField);

        // --- Save / cancel ---
        Button save = new Button("Save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> save());
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> Modal.close());
        HBox mainButtons = new HBox(10, save, cancel);

        // --- Other actions ---
        Button folder = smallButton("Open folder", () -> actions.openFolder(instance));
        Button duplicate = smallButton("Duplicate", () -> {
            Modal.close();
            actions.duplicate(instance);
        });
        Button export = smallButton("Export…", () -> actions.export(instance, getScene().getWindow()));
        Button code = smallButton("Copy setup code", () -> actions.copySetupCode(instance));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(event -> actions.confirmDelete(instance));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox otherActions = new HBox(8, folder, duplicate, export, code, spacer, delete);
        otherActions.setAlignment(Pos.CENTER_LEFT);
        otherActions.getStyleClass().add("settings-actions");

        getChildren().addAll(title, form, mainButtons, otherActions);
    }

    /** Saves every change at once. */
    private void save() {
        String name = nameField.getText().trim();
        Instance updated = instance
                .withName(name.isEmpty() ? instance.name() : name)
                .withVersion(versionBox.getValue())
                .withRamMb(ownRam.isSelected() ? snappedRam() : 0)
                .withJvmArgs(jvmArgsField.getText().trim());
        if (!loaderBox.getValue().equals(instance.loaderOrVanilla())) {
            updated = updated.withLoader(loaderBox.getValue());
        }
        updated = store.update(updated);
        panel.select(updated);
        Modal.close();
        Toast.info("Saved \"" + updated.name() + "\".");
    }

    private int snappedRam() {
        return (int) (Math.round(ramSlider.getValue() / 512.0) * 512);
    }

    private static String loaderName(String loader) {
        return switch (loader) {
            case Instance.FABRIC -> "Fabric";
            case Instance.FORGE -> "Forge (coming soon)";
            default -> "Vanilla (no mods)";
        };
    }

    private static void addRow(GridPane grid, int row, String name, javafx.scene.Node field) {
        Label label = new Label(name);
        label.getStyleClass().add("hardware-name");
        label.setMinWidth(Region.USE_PREF_SIZE); // never cut the label short
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.addRow(row, label, field);
    }

    private static Button smallButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    /** Shows each dropdown value as friendly text. */
    private static StringConverter<String> converter(java.util.function.Function<String, String> describe) {
        return new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null ? "" : describe.apply(value);
            }

            @Override
            public String fromString(String text) {
                return null; // typing into the box isn't allowed
            }
        };
    }
}
