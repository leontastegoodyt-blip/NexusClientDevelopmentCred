package com.nexusclient.launcher.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.nexusclient.launcher.game.NexusPaths;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * AccountManager – the player's signed-in Microsoft accounts (up to 3) and which one is active.
 *
 * Two kinds of information are kept, in two different places:
 *   - accounts.json (normal file): each account's Minecraft name, unique ID and skin link.
 *     None of that is secret – anyone can look up a player's name and skin.
 *   - SecretStore (encrypted by the operating system): each account's refresh token – the secret
 *     that lets Nexus sign in again without a password. Never saved as plain text.
 */
public final class AccountManager {

    public static final int MAX_ACCOUNTS = 3;

    /** One saved account (no secrets in here). */
    public record SavedAccount(String uuid, String username, String skinUrl) {}

    /** The shape of accounts.json. */
    private record SavedAccounts(String activeUuid, List<SavedAccount> accounts) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private final Path file = NexusPaths.root().resolve("accounts.json");
    private final ObservableList<SavedAccount> accounts = FXCollections.observableArrayList();
    private final ObjectProperty<SavedAccount> active = new SimpleObjectProperty<>();
    private final SecretStore secrets = SecretStore.forThisComputer();
    private final MicrosoftAuth auth = new MicrosoftAuth();

    /**
     * If the computer's keychain doesn't work, secrets are kept here in memory instead – so the
     * account still works until the launcher closes, but nothing is written to disk.
     */
    private final Map<String, String> memoryOnlySecrets = new ConcurrentHashMap<>();

    public AccountManager() {
        load();
    }

    /** All saved accounts. */
    public ObservableList<SavedAccount> accounts() {
        return accounts;
    }

    /** The account the game will be started with (null = nobody signed in). */
    public ObjectProperty<SavedAccount> activeProperty() {
        return active;
    }

    public SavedAccount getActive() {
        return active.get();
    }

    public boolean isFull() {
        return accounts.size() >= MAX_ACCOUNTS;
    }

    // ------------------------------------------------------------------ signing in

    /**
     * Opens the browser for a new sign-in and waits for it to finish.
     * Run on a background thread, then pass the result to addSignedIn() on the UI thread.
     */
    public MicrosoftAuth.LoginResult signIn(Consumer<String> openBrowser) throws LoginException, InterruptedException {
        return auth.signIn(openBrowser);
    }

    /**
     * Saves a freshly signed-in account and makes it the active one. Call on the UI thread.
     *
     * @return false if its secret couldn't be stored safely (the account then only works until
     *         the launcher closes)
     */
    public boolean addSignedIn(MicrosoftAuth.LoginResult result) {
        Account account = result.account();
        SavedAccount saved = new SavedAccount(account.uuid(), account.username(), result.skinUrl());
        boolean storedSafely = storeSecret(account.uuid(), result.refreshToken());

        int existing = indexOf(account.uuid());
        if (existing >= 0) {
            accounts.set(existing, saved); // signed in to the same account again: just update it
        } else if (!isFull()) {
            accounts.add(saved);
        }
        active.set(saved);
        save();
        return storedSafely;
    }

    /** Switches to another saved account. */
    public void setActive(SavedAccount account) {
        active.set(account);
        save();
    }

    /** Signs an account out of Nexus and deletes its saved secret. */
    public void remove(SavedAccount account) {
        try {
            secrets.delete(secretKey(account.uuid()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        memoryOnlySecrets.remove(secretKey(account.uuid()));
        accounts.removeIf(saved -> saved.uuid().equals(account.uuid()));
        if (active.get() != null && active.get().uuid().equals(account.uuid())) {
            active.set(accounts.isEmpty() ? null : accounts.get(0));
        }
        save();
    }

    /**
     * Gets an account ready to play: signs in again with its saved secret (no password needed),
     * so the Minecraft pass is fresh. Run on a background thread (it talks to the internet).
     */
    public Account accountForLaunch(SavedAccount account) throws LoginException, InterruptedException {
        String refreshToken = loadSecret(account.uuid());
        if (refreshToken == null) {
            throw new LoginException("Please sign in to " + account.username() + " again (click the account "
                    + "in the top-right corner).");
        }
        MicrosoftAuth.LoginResult result = auth.refresh(refreshToken);
        storeSecret(account.uuid(), result.refreshToken()); // Microsoft gives a new one each time

        // The player may have changed their name or skin since last time – update the saved copy.
        SavedAccount updated = new SavedAccount(result.account().uuid(), result.account().username(), result.skinUrl());
        Platform.runLater(() -> {
            int index = indexOf(updated.uuid());
            if (index >= 0 && !accounts.get(index).equals(updated)) {
                accounts.set(index, updated);
                if (active.get() != null && active.get().uuid().equals(updated.uuid())) {
                    active.set(updated);
                }
                save();
            }
        });
        return result.account();
    }

    // ------------------------------------------------------------------ secrets

    private static String secretKey(String uuid) {
        return "account-" + uuid;
    }

    /** Stores a secret safely. Returns false if only a memory copy could be kept. */
    private boolean storeSecret(String uuid, String secret) {
        try {
            secrets.save(secretKey(uuid), secret);
            memoryOnlySecrets.remove(secretKey(uuid));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            memoryOnlySecrets.put(secretKey(uuid), secret);
            return false;
        }
    }

    private String loadSecret(String uuid) {
        String inMemory = memoryOnlySecrets.get(secretKey(uuid));
        if (inMemory != null) {
            return inMemory;
        }
        try {
            return secrets.load(secretKey(uuid));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ------------------------------------------------------------------ accounts.json

    private int indexOf(String uuid) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).uuid().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            SavedAccounts saved = GSON.fromJson(Files.readString(file), SavedAccounts.class);
            if (saved != null && saved.accounts() != null) {
                accounts.setAll(saved.accounts());
                int activeIndex = saved.activeUuid() == null ? -1 : indexOf(saved.activeUuid());
                active.set(activeIndex >= 0 ? accounts.get(activeIndex) : (accounts.isEmpty() ? null : accounts.get(0)));
            }
        } catch (IOException | JsonParseException e) {
            e.printStackTrace(); // a damaged file just means nobody is signed in
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("accounts.json.tmp");
            String activeUuid = active.get() == null ? null : active.get().uuid();
            Files.writeString(temp, GSON.toJson(new SavedAccounts(activeUuid, List.copyOf(accounts))));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
