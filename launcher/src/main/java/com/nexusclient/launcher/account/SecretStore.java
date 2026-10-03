package com.nexusclient.launcher.account;

import com.nexusclient.launcher.game.NexusPaths;
import com.nexusclient.launcher.game.OperatingSystem;
import com.sun.jna.platform.win32.Crypt32Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SecretStore – keeps login secrets safe using the operating system's own protection.
 * Secrets are NEVER written to disk as plain text (the project brief, Accounts).
 *
 *   Windows: encrypted with Windows' "DPAPI" – the same protection browsers use for saved
 *            passwords. Only your Windows user account on this PC can decrypt it.
 *   Mac:     stored in the macOS Keychain.
 *   Linux:   stored in the system keyring (via the "secret-tool" program).
 *
 * Each secret has a "key" (a name to find it by), e.g. "account-<player uuid>".
 */
public interface SecretStore {

    /** Saves (or replaces) a secret. */
    void save(String key, String secret) throws IOException;

    /** Reads a secret, or returns null if there isn't one with that key. */
    String load(String key) throws IOException;

    /** Deletes a secret (does nothing if it doesn't exist). */
    void delete(String key) throws IOException;

    /** The right kind of secret store for this computer. */
    static SecretStore forThisComputer() {
        return switch (OperatingSystem.current()) {
            case WINDOWS -> new WindowsStore();
            case MAC -> new MacStore();
            case LINUX -> new LinuxStore();
        };
    }

    // ================================================================== Windows

    /** Encrypts each secret with DPAPI and saves the encrypted bytes in the Nexus "secrets" folder. */
    final class WindowsStore implements SecretStore {
        private final Path dir = NexusPaths.root().resolve("secrets");

        @Override
        public void save(String key, String secret) throws IOException {
            Files.createDirectories(dir);
            try {
                byte[] encrypted = Crypt32Util.cryptProtectData(secret.getBytes(StandardCharsets.UTF_8));
                Files.write(file(key), encrypted);
            } catch (RuntimeException e) { // JNA reports Windows errors as RuntimeExceptions
                throw new IOException("Windows couldn't encrypt the login", e);
            }
        }

        @Override
        public String load(String key) throws IOException {
            Path file = file(key);
            if (!Files.exists(file)) {
                return null;
            }
            try {
                return new String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(file)), StandardCharsets.UTF_8);
            } catch (RuntimeException e) {
                throw new IOException("Windows couldn't decrypt the saved login", e);
            }
        }

        @Override
        public void delete(String key) throws IOException {
            Files.deleteIfExists(file(key));
        }

        private Path file(String key) {
            return dir.resolve(NexusPaths.safeFileName(key) + ".dat");
        }
    }

    // ================================================================== Mac

    /** Uses the Mac's built-in "security" program to talk to the Keychain. */
    final class MacStore implements SecretStore {
        private static final String SERVICE = "NexusClient";

        @Override
        public void save(String key, String secret) throws IOException {
            // -U = update it if it already exists. (The Mac "security" tool only accepts the secret as
            // an argument, so it's briefly visible to other programs on this Mac while it saves.)
            run(null, "security", "add-generic-password", "-U", "-s", SERVICE, "-a", key, "-w", secret);
        }

        @Override
        public String load(String key) throws IOException {
            Result result = run(null, "security", "find-generic-password", "-s", SERVICE, "-a", key, "-w");
            return result.exitCode == 0 ? result.output.trim() : null;
        }

        @Override
        public void delete(String key) throws IOException {
            run(null, "security", "delete-generic-password", "-s", SERVICE, "-a", key);
        }
    }

    // ================================================================== Linux

    /** Uses the "secret-tool" program to talk to the system keyring (GNOME Keyring / KWallet). */
    final class LinuxStore implements SecretStore {
        private static final String SERVICE = "NexusClient";

        @Override
        public void save(String key, String secret) throws IOException {
            // secret-tool reads the secret from its input, so it never appears in the process list.
            Result result = run(secret, "secret-tool", "store", "--label=Nexus Client login",
                    "service", SERVICE, "account", key);
            if (result.exitCode != 0) {
                throw new IOException("The system keyring refused to save the login: " + result.output);
            }
        }

        @Override
        public String load(String key) throws IOException {
            Result result = run(null, "secret-tool", "lookup", "service", SERVICE, "account", key);
            return result.exitCode == 0 && !result.output.isBlank() ? result.output.trim() : null;
        }

        @Override
        public void delete(String key) throws IOException {
            run(null, "secret-tool", "clear", "service", SERVICE, "account", key);
        }
    }

    // ================================================================== helpers

    /** What a program printed, and its exit code (0 = success). */
    record Result(int exitCode, String output) {}

    /** Runs a program, optionally typing some text into it, and returns what it printed. */
    private static Result run(String input, String... command) throws IOException {
        Process process = new ProcessBuilder(new ArrayList<>(List.of(command)))
                .redirectErrorStream(true)
                .start();
        if (input != null) {
            try (OutputStream in = process.getOutputStream()) {
                in.write(input.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            process.getOutputStream().close();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream out = process.getInputStream()) {
            out.transferTo(output);
        }
        try {
            return new Result(process.waitFor(), output.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while using the keychain", e);
        }
    }
}
