package com.nexusclient.launcher.online;

import com.nexusclient.launcher.DevMode;
import com.nexusclient.launcher.game.NexusPaths;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * LocalServer – runs the Nexus online server (friends, clans, parties…) on THIS computer.
 *
 * Until Nexus has a real server on the internet (the project brief: "Where to host the backend"), the
 * launcher talks to http://localhost:8080 – this PC. Nobody was running a server there, so Friends
 * and Clans could never connect. Now, when the launcher needs the server and it isn't running,
 * it starts it by itself in the background, and stops it again when the launcher closes.
 *
 *   - Which server program: the backend's .jar file. `gradlew :launcher:run` builds it and tells
 *     the launcher where it is (-Dnexus.localServerJar).
 *   - Its database lives in <Nexus folder>/server/data, its log in <Nexus folder>/server/server.log.
 *   - It only listens to this PC (127.0.0.1), so nobody else on the network can reach it.
 *
 * Only friends who use the SAME computer can meet on it – real online play needs the hosted server.
 */
public final class LocalServer {

    private static Process process;

    private LocalServer() {}

    /**
     * Makes sure the server at "baseUrl" answers. If it's this PC and nothing is running, starts the
     * local server and waits (up to a minute) until it's ready. Does nothing for a real online server.
     *
     * @throws IOException with a plain-English reason if the server can't be reached or started
     */
    public static synchronized void ensureRunning(String baseUrl) throws IOException {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        boolean thisPc = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        if (!thisPc || answers(port)) {
            return; // a real online server, or the local one is already running
        }
        if (process == null || !process.isAlive()) {
            start(port);
        }
        // Spring Boot needs a few seconds to start up.
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (answers(port)) {
                return;
            }
            if (!process.isAlive()) {
                throw new IOException("The Nexus server stopped while starting – see "
                        + serverFolder().resolve("server.log"));
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Stopped waiting for the Nexus server");
            }
        }
        throw new IOException("The Nexus server took too long to start – see " + serverFolder().resolve("server.log"));
    }

    /** True if something is listening on that port on this PC. */
    private static boolean answers(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void start(int port) throws IOException {
        Path jar = serverJar();
        if (jar == null) {
            throw new IOException("The Nexus server isn't running, and this copy of Nexus doesn't include it. "
                    + "Start the launcher with gradlew :launcher:run.");
        }
        Path folder = serverFolder();
        Files.createDirectories(folder);

        // The same Java that runs the launcher (Java 21+, which the server needs too).
        String java = Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx512m", "-jar", jar.toString(),
                "--spring.profiles.active=dev",           // small file database (no PostgreSQL needed)
                "--server.address=127.0.0.1",              // only this PC can connect
                "--server.port=" + port,
                "--nexus.dev-auth=" + DevMode.enabled())); // test sign-in only in developer mode
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(folder.toFile())
                .redirectErrorStream(true)
                .redirectOutput(folder.resolve("server.log").toFile());
        process = builder.start();
        System.out.println("[Nexus] Started the local Nexus server (" + jar.getFileName() + ")");

        // Stop it again when the launcher closes.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }, "nexus-server-stop"));
    }

    /** The server program: from -Dnexus.localServerJar, if it exists. */
    private static Path serverJar() {
        String property = System.getProperty("nexus.localServerJar");
        if (property != null && Files.isRegularFile(Path.of(property))) {
            return Path.of(property);
        }
        return null;
    }

    private static Path serverFolder() {
        return NexusPaths.root().resolve("server");
    }
}
