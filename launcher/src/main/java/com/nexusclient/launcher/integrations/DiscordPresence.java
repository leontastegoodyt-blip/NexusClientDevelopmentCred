package com.nexusclient.launcher.integrations;

import com.google.gson.JsonObject;
import com.nexusclient.launcher.game.OperatingSystem;
import com.nexusclient.launcher.settings.SettingsStore;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * DiscordPresence – shows "Playing on Nexus Client" on the player's Discord profile while they play
 * (the project brief: Discord Rich Presence on/off).
 *
 * How it works: the Discord app on the player's computer listens on a local "pipe" (a private
 * connection between programs on the same computer). We connect, say who we are (our Discord
 * application ID), and send the activity to show. When the game closes, we disconnect and it
 * disappears.
 *
 * SETUP NEEDED: create an application at https://discord.com/developers/applications (named
 * "Nexus Client", with an image asset called "nexus"), then put its Application ID in CLIENT_ID.
 * Until then this does nothing.
 */
public final class DiscordPresence {

    /** Nexus's Discord application ID. Null = not set up yet (see the note above). */
    public static final String CLIENT_ID = null;

    private static RandomAccessFile windowsPipe;
    private static SocketChannel unixSocket;

    private DiscordPresence() {}

    /** True if Discord status can work (an application ID is set up). */
    public static boolean isConfigured() {
        return CLIENT_ID != null;
    }

    /**
     * Shows the activity on Discord (if the player has it switched on and Discord is running).
     * Quietly does nothing if Discord isn't open. Runs its own background thread.
     *
     * @param details e.g. "Playing Minecraft 1.20.1"
     */
    public static void show(String details) {
        if (!isConfigured() || !SettingsStore.get().discordPresence) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                connect();
                JsonObject handshake = new JsonObject();
                handshake.addProperty("v", 1);
                handshake.addProperty("client_id", CLIENT_ID);
                send(0, handshake.toString());
                read(); // Discord's hello

                JsonObject activity = new JsonObject();
                activity.addProperty("details", details);
                activity.addProperty("state", "on Nexus Client");
                JsonObject timestamps = new JsonObject();
                timestamps.addProperty("start", System.currentTimeMillis() / 1000);
                activity.add("timestamps", timestamps);
                JsonObject assets = new JsonObject();
                assets.addProperty("large_image", "nexus");
                assets.addProperty("large_text", "Nexus Client");
                activity.add("assets", assets);

                JsonObject args = new JsonObject();
                args.addProperty("pid", ProcessHandle.current().pid());
                args.add("activity", activity);
                JsonObject command = new JsonObject();
                command.addProperty("cmd", "SET_ACTIVITY");
                command.add("args", args);
                command.addProperty("nonce", UUID.randomUUID().toString());
                send(1, command.toString());
                read();
            } catch (IOException e) {
                close(); // Discord isn't running – that's fine
            }
        }, "discord-presence");
        thread.setDaemon(true);
        thread.start();
    }

    /** Removes the activity (by disconnecting). */
    public static void clear() {
        close();
    }

    // ------------------------------------------------------------------ the pipe

    /** Discord listens on "discord-ipc-0" (or -1, -2… if several Discords are open). */
    private static void connect() throws IOException {
        close();
        for (int i = 0; i < 10; i++) {
            try {
                if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
                    windowsPipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                } else {
                    Path socket = Path.of(tempDir(), "discord-ipc-" + i);
                    if (!Files.exists(socket)) {
                        continue;
                    }
                    unixSocket = SocketChannel.open(UnixDomainSocketAddress.of(socket));
                }
                return;
            } catch (IOException e) {
                // try the next number
            }
        }
        throw new IOException("Discord isn't running");
    }

    private static String tempDir() {
        for (String variable : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String value = System.getenv(variable);
            if (value != null) {
                return value;
            }
        }
        return "/tmp";
    }

    /** Each message is: an "opcode" number, the message length, then the JSON text (little-endian numbers). */
    private static void send(int opcode, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode).putInt(body.length).put(body).flip();
        if (windowsPipe != null) {
            windowsPipe.write(frame.array());
        } else if (unixSocket != null) {
            while (frame.hasRemaining()) {
                unixSocket.write(frame);
            }
        }
    }

    private static void read() throws IOException {
        byte[] header = new byte[8];
        if (windowsPipe != null) {
            windowsPipe.readFully(header);
            int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
            windowsPipe.readFully(new byte[length]);
        } else if (unixSocket != null) {
            ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (head.hasRemaining() && unixSocket.read(head) >= 0) { }
            ByteBuffer body = ByteBuffer.allocate(head.getInt(4));
            while (body.hasRemaining() && unixSocket.read(body) >= 0) { }
        }
    }

    private static void close() {
        try {
            if (windowsPipe != null) windowsPipe.close();
            if (unixSocket != null) unixSocket.close();
        } catch (IOException ignored) {
            // already closed
        }
        windowsPipe = null;
        unixSocket = null;
    }
}
