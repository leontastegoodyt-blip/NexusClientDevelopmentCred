package com.nexusclient.core.online;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * LiveSocket – the game's live line to the Nexus server (the WebSocket at /ws), so the server can
 * tell us things the moment they happen: a friend came online, a party message arrived…
 *
 * Java 8 (which 1.8.9 runs on) has no WebSocket client, so this is a small one written by hand,
 * following the WebSocket standard (RFC 6455):
 *   1. Connect, and ask (in plain HTTP) to "upgrade" the connection to a WebSocket, sending the
 *      player's sign-in. The server answers "101 Switching Protocols".
 *   2. From then on both sides send "frames": a couple of header bytes (type + length), then the
 *      text. Frames we send must be "masked" (mixed with 4 random bytes) – the standard requires it.
 *   3. "ping" frames are answered with "pong"; a "close" frame ends the connection.
 * If the line drops, it reconnects by itself after a short wait (longer each time, up to a minute).
 */
public final class LiveSocket {

    /** What happens with the messages that arrive (called on the socket's own thread). */
    public interface Listener {
        void connected();

        void message(String json);

        void disconnected();
    }

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"; // fixed by the standard
    private static final int MAX_MESSAGE = 1024 * 1024;

    private final String backend;
    private final String token;
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private volatile boolean running;
    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile boolean open;

    public LiveSocket(String backend, String token, Listener listener) {
        this.backend = backend;
        this.token = token;
        this.listener = listener;
    }

    /** Starts connecting in the background (and keeps reconnecting until stop()). */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "Nexus live");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
    }

    public boolean isOpen() {
        return open;
    }

    /** Sends a text message. Returns false if the line isn't open right now. */
    public boolean send(String text) {
        OutputStream stream = out;
        if (!open || stream == null) {
            return false;
        }
        try {
            writeFrame(stream, 0x1, text.getBytes("UTF-8"));
            return true;
        } catch (IOException e) {
            closeQuietly();
            return false;
        }
    }

    // ------------------------------------------------------------------ the connection loop

    private void loop() {
        long wait = 3000;
        while (running) {
            try {
                connect();
                wait = 3000; // connected fine – next time, retry quickly again
                open = true;
                listener.connected();
                readFrames();
            } catch (Exception e) {
                // Server down, no internet, signed out… try again later.
            } finally {
                boolean wasOpen = open;
                open = false;
                closeQuietly();
                if (wasOpen) {
                    listener.disconnected();
                }
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
            wait = Math.min(60000, wait * 2);
        }
    }

    /** Opens the connection and does the "upgrade to WebSocket" handshake. */
    private void connect() throws IOException {
        URL url = new URL(backend);
        boolean secure = "https".equalsIgnoreCase(url.getProtocol());
        int port = url.getPort() > 0 ? url.getPort() : secure ? 443 : 80;
        String host = url.getHost();
        Socket s = secure ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        s.connect(new InetSocketAddress(host, port), 8000);
        // No data for 90 seconds = the line is dead (we send a ping every 25 s and the server answers).
        s.setSoTimeout(90000);
        socket = s;
        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);
        String request = "GET /ws HTTP/1.1\r\n"
                + "Host: " + host + (url.getPort() > 0 ? ":" + port : "") + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Authorization: Bearer " + token + "\r\n"
                + "\r\n";
        OutputStream stream = s.getOutputStream();
        stream.write(request.getBytes("UTF-8"));
        stream.flush();

        String headers = readHeaders(s.getInputStream());
        if (!headers.startsWith("HTTP/1.1 101")) {
            throw new IOException("The Nexus server refused the live connection: "
                    + headers.split("\r\n")[0]);
        }
        // The server proves it understood by sending back a value worked out from our key.
        String expected = acceptValue(key);
        if (!headers.toLowerCase(java.util.Locale.ROOT).contains("sec-websocket-accept: " + expected.toLowerCase(java.util.Locale.ROOT))) {
            throw new IOException("The live connection answered strangely.");
        }
        out = stream;
    }

    /** Reads the HTTP answer up to the empty line that ends its headers. */
    private static String readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int last4 = 0; // the last four bytes read, packed together
        while (last4 != 0x0D0A0D0A) { // "\r\n\r\n" = the empty line after the headers
            int b = in.read();
            if (b < 0) {
                throw new IOException("closed during handshake");
            }
            bytes.write(b);
            last4 = (last4 << 8) | b;
            if (bytes.size() > 16384) {
                throw new IOException("handshake too long");
            }
        }
        return new String(bytes.toByteArray(), "UTF-8");
    }

    static String acceptValue(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return Base64.getEncoder().encodeToString(sha1.digest((key + GUID).getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ frames

    /** Reads frames until the connection closes, handing complete text messages to the listener. */
    private void readFrames() throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (running) {
            int first = in.readUnsignedByte();
            int second = in.readUnsignedByte();
            boolean fin = (first & 0x80) != 0;
            int opcode = first & 0x0F;
            long length = second & 0x7F;
            if (length == 126) {
                length = in.readUnsignedShort();
            } else if (length == 127) {
                length = in.readLong();
            }
            if (length < 0 || length > MAX_MESSAGE) {
                throw new IOException("message too big");
            }
            byte[] mask = null;
            if ((second & 0x80) != 0) { // servers don't normally mask, but the standard allows reading it
                mask = new byte[4];
                in.readFully(mask);
            }
            byte[] payload = new byte[(int) length];
            in.readFully(payload);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }
            switch (opcode) {
                case 0x0: // the next piece of a long message
                case 0x1: // text
                    message.write(payload);
                    if (message.size() > MAX_MESSAGE) {
                        throw new IOException("message too big");
                    }
                    if (fin) {
                        String text = new String(message.toByteArray(), "UTF-8");
                        message.reset();
                        try {
                            listener.message(text);
                        } catch (RuntimeException e) {
                            System.err.println("[Nexus] Couldn't handle a live message: " + e);
                        }
                    }
                    break;
                case 0x8: // close
                    try {
                        writeFrame(out, 0x8, new byte[0]);
                    } catch (IOException ignored) {
                        // closing anyway
                    }
                    return;
                case 0x9: // ping → pong with the same bytes
                    writeFrame(out, 0xA, payload);
                    break;
                default: // pong (0xA) and binary frames: nothing to do
                    break;
            }
        }
    }

    /** Writes one complete, masked frame. */
    private void writeFrame(OutputStream stream, int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 14);
        frame.write(0x80 | opcode); // "fin" (this is the whole message) + the type
        if (payload.length < 126) {
            frame.write(0x80 | payload.length); // 0x80 = masked
        } else if (payload.length < 65536) {
            frame.write(0x80 | 126);
            frame.write(payload.length >> 8);
            frame.write(payload.length);
        } else {
            frame.write(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.write((int) ((long) payload.length >> shift));
            }
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        synchronized (this) {
            stream.write(frame.toByteArray());
            stream.flush();
        }
    }

    private void closeQuietly() {
        Socket s = socket;
        socket = null;
        out = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
