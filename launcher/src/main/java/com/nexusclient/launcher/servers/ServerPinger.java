package com.nexusclient.launcher.servers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;

/**
 * ServerPinger – asks a Minecraft server how it's doing, exactly like the game's own server list:
 * how many players are online, its message of the day, its icon, and how fast it answers (ping).
 *
 * It speaks Minecraft's "Server List Ping" language (used since 1.7):
 *   1. connect and say hello ("handshake"), saying we want the server's status
 *   2. ask for the status → the server answers with a JSON description
 *   3. send a "ping" with a number and time how long the "pong" takes to come back
 * Docs: https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping
 */
public final class ServerPinger {

    /**
     * What a server said.
     *
     * @param online   false if the server couldn't be reached
     * @param players  players online now
     * @param max      the most players it allows
     * @param version  the version it runs, e.g. "Requires MC 1.8 / 1.21"
     * @param motd     its "message of the day" as plain text
     * @param pingMs   how long it took to answer, in milliseconds
     * @param icon     its 64x64 PNG icon (null if it has none)
     */
    public record Status(boolean online, int players, int max, String version, String motd, long pingMs, byte[] icon) {

        public static Status offline() {
            return new Status(false, 0, 0, "", "Can't reach this server right now.", -1, null);
        }
    }

    private static final int TIMEOUT_MS = 4000;

    private ServerPinger() {}

    /** Pings a server address like "mc.hypixel.net" or "play.example.com:25566". Never throws. */
    public static Status ping(String address) {
        try {
            HostPort target = resolve(address);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target.host(), target.port()), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // 1. Handshake: protocol version (-1 = "just asking"), address, port, next state 1 = status.
                ByteArrayOutputStream handshake = new ByteArrayOutputStream();
                DataOutputStream packet = new DataOutputStream(handshake);
                writeVarInt(packet, 0x00);
                writeVarInt(packet, -1);
                writeString(packet, target.host());
                packet.writeShort(target.port());
                writeVarInt(packet, 1);
                sendPacket(out, handshake.toByteArray());

                // 2. Status request (an empty packet 0x00), then read the JSON answer.
                sendPacket(out, new byte[] {0x00});
                readVarInt(in); // total length (not needed)
                if (readVarInt(in) != 0x00) {
                    return Status.offline();
                }
                byte[] jsonBytes = new byte[readVarInt(in)];
                in.readFully(jsonBytes);
                JsonObject json = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();

                // 3. Ping: send the current time, time the answer.
                long start = System.nanoTime();
                ByteArrayOutputStream ping = new ByteArrayOutputStream();
                DataOutputStream pingPacket = new DataOutputStream(ping);
                writeVarInt(pingPacket, 0x01);
                pingPacket.writeLong(System.currentTimeMillis());
                sendPacket(out, ping.toByteArray());
                long pingMs;
                try {
                    readVarInt(in);
                    readVarInt(in);
                    in.readLong();
                    pingMs = (System.nanoTime() - start) / 1_000_000;
                } catch (IOException e) {
                    pingMs = -1; // some servers close the connection instead of answering the ping
                }
                return toStatus(json, pingMs);
            }
        } catch (Exception e) {
            return Status.offline();
        }
    }

    /** Turns the server's JSON answer into a Status. */
    private static Status toStatus(JsonObject json, long pingMs) {
        int online = 0;
        int max = 0;
        if (json.has("players")) {
            JsonObject players = json.getAsJsonObject("players");
            online = players.has("online") ? players.get("online").getAsInt() : 0;
            max = players.has("max") ? players.get("max").getAsInt() : 0;
        }
        String version = json.has("version") && json.getAsJsonObject("version").has("name")
                ? stripColours(json.getAsJsonObject("version").get("name").getAsString()) : "";
        String motd = json.has("description") ? stripColours(flattenText(json.get("description"))).trim() : "";
        byte[] icon = null;
        if (json.has("favicon")) {
            String favicon = json.get("favicon").getAsString();
            int comma = favicon.indexOf(',');
            if (comma > 0) {
                try {
                    icon = Base64.getMimeDecoder().decode(favicon.substring(comma + 1));
                } catch (IllegalArgumentException ignored) {
                    // broken icon – show none
                }
            }
        }
        return new Status(true, online, max, version, motd, pingMs, icon);
    }

    /**
     * The message of the day can be plain text or Minecraft's "chat component" format
     * ({"text": "...", "extra": [...]}) – this turns either into plain text.
     */
    private static String flattenText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder text = new StringBuilder();
            element.getAsJsonArray().forEach(part -> text.append(flattenText(part)));
            return text.toString();
        }
        JsonObject object = element.getAsJsonObject();
        StringBuilder text = new StringBuilder(object.has("text") ? object.get("text").getAsString() : "");
        if (object.has("extra")) {
            text.append(flattenText(object.get("extra")));
        }
        return text.toString();
    }

    /** Removes Minecraft's colour codes (§ followed by a character). */
    private static String stripColours(String text) {
        return text.replaceAll("§.", "");
    }

    // ------------------------------------------------------------------ addresses

    private record HostPort(String host, int port) {}

    /**
     * Works out where to connect. "play.example.com:25566" has its own port. Otherwise the server
     * may have an "SRV record" in DNS (the internet's address book) pointing to the real host and
     * port; if not, Minecraft's normal port 25565 is used.
     */
    private static HostPort resolve(String address) {
        String trimmed = address.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && trimmed.indexOf(':') == colon) {
            try {
                return new HostPort(trimmed.substring(0, colon), Integer.parseInt(trimmed.substring(colon + 1)));
            } catch (NumberFormatException ignored) {
                // not a port number – treat it as a plain address
            }
        }
        try {
            Hashtable<String, String> environment = new Hashtable<>();
            environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            Attributes records = new InitialDirContext(environment)
                    .getAttributes("_minecraft._tcp." + trimmed, new String[] {"SRV"});
            Attribute srv = records.get("SRV");
            if (srv != null) {
                // e.g. "0 5 25565 mc.example.com." → priority, weight, port, host
                String[] parts = srv.get().toString().split(" ");
                String host = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                return new HostPort(host, Integer.parseInt(parts[2]));
            }
        } catch (Exception ignored) {
            // no SRV record – that's normal
        }
        return new HostPort(trimmed, 25565);
    }

    // ------------------------------------------------------------------ Minecraft's number format

    /**
     * Minecraft sends many numbers as "VarInts": small numbers use fewer bytes. Each byte carries
     * 7 bits of the number, and its top bit says "more bytes follow".
     */
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            byte current = in.readByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 32) {
                throw new IOException("Number too long");
            }
        }
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /** Sends a packet: its length first, then its bytes. */
    private static void sendPacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }
}
