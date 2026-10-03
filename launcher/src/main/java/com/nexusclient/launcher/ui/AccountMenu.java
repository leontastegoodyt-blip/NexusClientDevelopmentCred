package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.DevMode;
import com.nexusclient.launcher.account.AccountManager;
import com.nexusclient.launcher.account.AccountManager.SavedAccount;
import com.nexusclient.launcher.account.LoginException;
import com.nexusclient.launcher.account.MicrosoftAuth;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * AccountMenu – the account button in the top-right corner, and the menu it opens.
 *
 * The button shows the active account's skin head and name (or "Sign in"). The menu lists up to
 * 3 accounts – click one to switch to it – plus "Add Microsoft account" and a remove button on
 * each account (click it twice to confirm, so nobody removes an account by accident).
 */
public class AccountMenu {

    private final AccountManager accounts;
    private final Consumer<String> openBrowser;

    private final Button button = new Button();
    private final ContextMenu menu = new ContextMenu();

    /** The sign-in in progress (waiting for the browser), or null. */
    private Task<MicrosoftAuth.LoginResult> signingIn;

    /**
     * @param openBrowser opens a web address in the player's browser
     */
    public AccountMenu(AccountManager accounts, Consumer<String> openBrowser) {
        this.accounts = accounts;
        this.openBrowser = openBrowser;

        button.getStyleClass().add("account-button");
        button.setOnAction(event -> openMenu());
        menu.getStyleClass().add("account-menu");

        // Keep the button up to date with the active account.
        accounts.activeProperty().addListener((observable, oldAccount, newAccount) -> refreshButton());
        refreshButton();
    }

    /** The button to put in the title bar. */
    public Button button() {
        return button;
    }

    /** Shows the active account (or "Sign in") on the button. */
    private void refreshButton() {
        SavedAccount active = accounts.getActive();
        if (signingIn != null) {
            button.setText("Signing in…");
            button.setGraphic(SkinHeads.create(null, 22));
        } else if (active == null) {
            button.setText("Sign in");
            button.setGraphic(SkinHeads.create(null, 22));
        } else {
            button.setText(active.username());
            button.setGraphic(SkinHeads.create(active, 22));
        }
    }

    /** Builds the menu fresh each time it opens, so it always shows the latest accounts. */
    private void openMenu() {
        menu.getItems().clear();

        Label title = new Label("ACCOUNTS  " + accounts.accounts().size() + " / " + AccountManager.MAX_ACCOUNTS);
        title.getStyleClass().add("menu-title");
        menu.getItems().add(plainItem(title, false));

        for (SavedAccount account : accounts.accounts()) {
            menu.getItems().add(accountItem(account));
        }
        if (accounts.accounts().isEmpty()) {
            Label none = new Label(DevMode.enabled()
                    ? "Not signed in – the test player \"NexusDev\"\nis used (developer mode only)."
                    : "Not signed in yet.");
            none.getStyleClass().add("menu-hint");
            menu.getItems().add(plainItem(none, false));
        }

        menu.getItems().add(new SeparatorMenuItem());

        if (signingIn != null) {
            Label cancel = new Label("Cancel sign-in");
            cancel.getStyleClass().add("menu-action");
            CustomMenuItem item = plainItem(cancel, true);
            item.setOnAction(event -> signingIn.cancel());
            menu.getItems().add(item);
        } else {
            Label add = new Label("+  Add Microsoft account");
            add.getStyleClass().add("menu-action");
            CustomMenuItem item = plainItem(add, true);
            item.setDisable(accounts.isFull());
            item.setOnAction(event -> startSignIn());
            menu.getItems().add(item);
            if (accounts.isFull()) {
                Label full = new Label("You can have up to 3 accounts.\nRemove one to add another.");
                full.getStyleClass().add("menu-hint");
                menu.getItems().add(plainItem(full, false));
            }
        }

        menu.show(button, Side.BOTTOM, 0, 6);
    }

    /** One account row: head, name, "Active" or "Switch", and a remove button. */
    private CustomMenuItem accountItem(SavedAccount account) {
        boolean isActive = account.equals(accounts.getActive());

        Label name = new Label(account.username());
        name.getStyleClass().add("menu-account-name");
        Label status = new Label(isActive ? "Active" : "Click to switch");
        status.getStyleClass().add(isActive ? "menu-account-active" : "menu-hint");
        VBox text = new VBox(1, name, status);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Remove: the first click asks "Remove?", the second click really removes it.
        Button remove = new Button("✕");
        remove.getStyleClass().add("menu-remove");
        remove.setTooltip(new Tooltip("Sign this account out of Nexus"));
        remove.setOnAction(event -> {
            if (remove.getStyleClass().contains("confirm")) {
                accounts.remove(account);
                menu.hide();
                Toast.info("Signed out " + account.username() + ".");
            } else {
                remove.setText("Remove?");
                remove.getStyleClass().add("confirm");
            }
        });
        // Stop the click here, so it doesn't ALSO count as clicking the row (which would switch account).
        remove.addEventHandler(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
        remove.addEventHandler(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);

        HBox row = new HBox(10, SkinHeads.create(account, 30), text, spacer, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(250);
        if (isActive) {
            row.getStyleClass().add("menu-account-row-active");
        }

        CustomMenuItem item = new CustomMenuItem(row, true);
        item.setOnAction(event -> {
            accounts.setActive(account);
            Toast.info("Switched to " + account.username() + ".");
        });
        return item;
    }

    /** A menu row holding any node. clickable = whether the menu reacts/closes when it's clicked. */
    private static CustomMenuItem plainItem(javafx.scene.Node content, boolean clickable) {
        CustomMenuItem item = new CustomMenuItem(content, clickable);
        if (!clickable) {
            item.getStyleClass().add("menu-static");
        }
        return item;
    }

    // ------------------------------------------------------------------ signing in

    /**
     * Opens Microsoft's login page and waits for the player to finish, in the background.
     * (Public so the first-run screens can start a sign-in too.)
     */
    public void startSignIn() {
        if (signingIn != null || accounts.isFull()) {
            return;
        }
        Task<MicrosoftAuth.LoginResult> task = new Task<>() {
            @Override
            protected MicrosoftAuth.LoginResult call() throws Exception {
                // The browser must be opened from the UI thread, so hand that job over to it.
                return accounts.signIn(url -> Platform.runLater(() -> openBrowser.accept(url)));
            }
        };
        signingIn = task;
        refreshButton();
        Toast.info("Your browser opened Microsoft's sign-in page. Log in there, then come back here.");

        task.setOnSucceeded(event -> {
            signingIn = null;
            MicrosoftAuth.LoginResult result = task.getValue();
            boolean storedSafely = accounts.addSignedIn(result);
            refreshButton();
            Toast.info("Signed in as " + result.account().username() + ".");
            if (!storedSafely) {
                Toast.error("Couldn't save your sign-in securely on this computer, so you'll need to sign in "
                        + "again next time you open Nexus.");
            }
        });
        task.setOnFailed(event -> {
            signingIn = null;
            refreshButton();
            Throwable error = task.getException();
            if (error instanceof LoginException) {
                Toast.error(error.getMessage());
            } else {
                error.printStackTrace();
                Toast.error("Sign-in failed: " + error.getMessage());
            }
        });
        task.setOnCancelled(event -> {
            signingIn = null;
            refreshButton();
            Toast.info("Sign-in cancelled.");
        });

        Thread thread = new Thread(task, "microsoft-sign-in");
        thread.setDaemon(true);
        thread.start();
    }
}
