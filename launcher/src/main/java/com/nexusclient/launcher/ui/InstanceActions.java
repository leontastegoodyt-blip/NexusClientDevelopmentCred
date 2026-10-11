package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.mods.MrpackImporter;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * InstanceActions – the things you can do with an instance: open its folder, duplicate, export,
 * import, copy/paste a setup code, and delete. Used by the right-click menu, the + menu and the
 * instance settings pop-up, so each action is written only once.
 *
 * Slow jobs (copying folders, zipping) run in the background so the window doesn't freeze.
 */
public class InstanceActions {

    private final InstanceStore store;
    private final InstancePanel panel;

    public InstanceActions(InstanceStore store, InstancePanel panel) {
        this.store = store;
        this.panel = panel;
    }

    /** Opens the instance's folder in File Explorer / Finder. */
    public void openFolder(Instance instance) {
        Path dir = NexusPaths.instanceDir(instance);
        runInBackground("open-folder", () -> {
            Files.createDirectories(dir); // an instance that was never played has no folder yet
            Desktop.getDesktop().open(dir.toFile());
            return null;
        }, null, "Couldn't open the folder");
    }

    /** Copies an instance (including its worlds, mods and settings). */
    public void duplicate(Instance instance) {
        runInBackground("duplicate", () -> store.duplicate(instance), copy -> {
            panel.select(copy);
            Toast.info("Made a copy: \"" + copy.name() + "\".");
        }, "Couldn't copy the instance");
    }

    /** Asks where to save, then exports the instance's setup to a .zip file. */
    public void export(Instance instance, Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.nexusclient.launcher.i18n.Translator.tr("Export \"" + instance.name() + "\""));
        chooser.setInitialFileName(NexusPaths.safeFileName(instance.name()) + ".nexus.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Nexus instance", "*.zip"));
        File file = chooser.showSaveDialog(window);
        if (file == null) {
            return; // cancelled
        }
        runInBackground("export", () -> {
            store.export(instance, file.toPath());
            return null;
        }, done -> Toast.info("Exported \"" + instance.name() + "\" to " + file.getName()
                + ". (Worlds aren't included – just the setup.)"), "Couldn't export the instance");
    }

    /** Asks for a .zip made by Export, or a Modrinth modpack (.mrpack), then adds it as a new instance. */
    public void importFile(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(com.nexusclient.launcher.i18n.Translator.tr("Import an instance or modpack"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Nexus instance or Modrinth modpack", "*.zip", "*.mrpack"));
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return;
        }
        if (file.getName().toLowerCase().endsWith(".mrpack")) {
            importModpack(file.toPath());
            return;
        }
        runInBackground("import", () -> store.importZip(file.toPath()), imported -> {
            panel.select(imported);
            Toast.info("Imported \"" + imported.name() + "\".");
        }, "Couldn't import that file");
    }

    /** Installs a Modrinth modpack file as a new instance (downloads its mods – can take a while). */
    public void importModpack(Path mrpack) {
        Toast.info("Installing the modpack… this can take a minute.");
        runInBackground("modpack", () -> store.addExisting(
                MrpackImporter.importPack(mrpack, (message, fraction) -> {})), imported -> {
            panel.select(imported);
            Toast.info("Installed the modpack \"" + imported.name() + "\".");
        }, "Couldn't install that modpack");
    }

    /** Copies the instance's setup code, ready to paste to a friend. */
    public void copySetupCode(Instance instance) {
        ClipboardContent content = new ClipboardContent();
        content.putString(InstanceStore.setupCode(instance));
        Clipboard.getSystemClipboard().setContent(content);
        Toast.info("Setup code copied! Send it to a friend – they add it with + → Paste setup code.");
    }

    /** Shows a small pop-up where a setup code can be pasted. */
    public void pasteSetupCode() {
        Label title = new Label("Paste a setup code");
        title.getStyleClass().add("torcher-title");
        Label text = new Label("A setup code copies someone's instance: its version, mod loader and settings.");
        text.getStyleClass().add("torcher-text");
        text.setWrapText(true);
        TextField field = new TextField();
        field.setPromptText("NX1-…");
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString() && clipboard.getString().trim().startsWith("NX1-")) {
            field.setText(clipboard.getString().trim()); // save the player a step
        }
        Button add = new Button("Add instance");
        add.getStyleClass().add("primary-button");
        add.setOnAction(event -> {
            try {
                Instance imported = store.importCode(field.getText());
                Modal.close();
                panel.select(imported);
                Toast.info("Added \"" + imported.name() + "\" from the setup code.");
            } catch (IOException e) {
                Toast.error(e.getMessage());
            }
        });
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(event -> Modal.close());
        Modal.show(new VBox(14, title, text, field, new HBox(10, add, cancel)), 520);
        field.requestFocus();
    }

    /** Asks first, then deletes the instance (its folder goes to the Recycle Bin when possible). */
    public void confirmDelete(Instance instance) {
        Modal.confirm("Delete \"" + instance.name() + "\"?",
                "This removes the instance and its folder – including its worlds, mods and settings. "
                        + "On Windows and Mac the folder goes to the Recycle Bin / Trash, so it can still be recovered.",
                "Delete", () -> runInBackground("delete", () -> {
                    store.delete(instance);
                    return null;
                }, done -> Toast.info("Deleted \"" + instance.name() + "\"."), "Couldn't delete the instance"));
    }

    // ------------------------------------------------------------------ helper

    /** A job that can fail (e.g. a file can't be written). */
    private interface Job<T> {
        T run() throws Exception;
    }

    /** Runs a job on a background thread, then either onDone (on the UI thread) or an error toast. */
    private static <T> void runInBackground(String name, Job<T> job, java.util.function.Consumer<T> onDone,
                                            String errorMessage) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return job.run();
            }
        };
        task.setOnSucceeded(event -> {
            if (onDone != null) {
                onDone.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            task.getException().printStackTrace();
            Toast.error(errorMessage + ": " + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "instance-" + name);
        thread.setDaemon(true);
        thread.start();
    }
}
