package com.nexusclient.launcher.servers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexusclient.launcher.game.NexusPaths;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ServerStore – the lists of servers the launcher shows:
 *
 *   - Featured: well-known servers, from data/servers.json inside Nexus
 *   - Nexus Partners: servers that partner with Nexus (build step 15), also from that file
 *   - Favourites: servers the player saved (saved in servers.json in the Nexus folder)
 *   - Recent: the last servers the player joined through Nexus (also saved there)
 */
public final class ServerStore {

    /** One server in a list. */
    public record Server(String name, String address, String description) {}

    // The shapes of the JSON files.
    private record Bundled(List<Server> featured, List<Server> partners) {}
    private record Saved(List<Server> favourites, List<Server> recent) {}

    private static final int MAX_RECENT = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file = NexusPaths.root().resolve("servers.json");
    private final List<Server> featured = new ArrayList<>();
    private final ObservableList<Server> partners = FXCollections.observableArrayList();
    /** Extra details of each Partner Server from the Nexus server (by address). */
    private final java.util.Map<String, Partner> partnerDetails = new java.util.HashMap<>();

    /**
     * A Nexus Partner Server: which Nexus mods it switches off, and whether the signed-in player
     * manages it (then they may change those rules).
     */
    public record Partner(long id, String address, List<String> blockedMods, boolean youManage) {}
    private final ObservableList<Server> favourites = FXCollections.observableArrayList();
    private final ObservableList<Server> recent = FXCollections.observableArrayList();

    public ServerStore() {
        try (InputStream in = ServerStore.class.getResourceAsStream("/com/nexusclient/launcher/data/servers.json")) {
            Bundled bundled = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Bundled.class);
            featured.addAll(bundled.featured());
            partners.addAll(bundled.partners());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (Files.exists(file)) {
                Saved saved = GSON.fromJson(Files.readString(file), Saved.class);
                if (saved != null) {
                    if (saved.favourites() != null) favourites.setAll(saved.favourites());
                    if (saved.recent() != null) recent.setAll(saved.recent());
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // damaged file: start with empty lists
        }
    }

    public List<Server> featured() { return featured; }
    public ObservableList<Server> partners() { return partners; }

    public Partner partnerDetails(String address) { return partnerDetails.get(address); }

    /**
     * Loads the Nexus Partner Servers from the Nexus online server, in the background (the bundled
     * list in data/servers.json is used until then, or if the Nexus server can't be reached).
     */
    public void loadPartners() {
        com.nexusclient.launcher.ui.Background.run("partners", () -> {
            com.nexusclient.launcher.online.NexusOnline online = com.nexusclient.launcher.online.NexusOnline.get();
            return online.get(online.signedIn() ? "/api/partners/mine" : "/api/partners").getAsJsonArray();
        }, list -> {
            List<Server> loaded = new ArrayList<>();
            partnerDetails.clear();
            for (com.google.gson.JsonElement e : list) {
                com.google.gson.JsonObject p = e.getAsJsonObject();
                String address = p.get("address").getAsString();
                loaded.add(new Server(p.get("name").getAsString(), address,
                        p.has("description") && !p.get("description").isJsonNull() ? p.get("description").getAsString() : ""));
                List<String> blocked = new ArrayList<>();
                p.getAsJsonArray("blockedMods").forEach(id -> blocked.add(id.getAsString()));
                partnerDetails.put(address, new Partner(p.get("id").getAsLong(), address, blocked,
                        p.has("youManage") && p.get("youManage").getAsBoolean()));
            }
            partners.setAll(loaded);
        }, error -> { }); // offline: keep the bundled list
    }
    public ObservableList<Server> favourites() { return favourites; }
    public ObservableList<Server> recent() { return recent; }

    /** The server the player joined most recently, or null. */
    public Server lastJoined() {
        return recent.isEmpty() ? null : recent.get(0);
    }

    public boolean isFavourite(String address) {
        return favourites.stream().anyMatch(server -> server.address().equalsIgnoreCase(address));
    }

    /** Adds or removes a favourite. */
    public void setFavourite(Server server, boolean favourite) {
        favourites.removeIf(existing -> existing.address().equalsIgnoreCase(server.address()));
        if (favourite) {
            favourites.add(server);
        }
        save();
    }

    /** Remembers that the player joined a server (it goes to the top of Recent). */
    public void rememberJoined(Server server) {
        recent.removeIf(existing -> existing.address().equalsIgnoreCase(server.address()));
        recent.add(0, server);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        save();
    }

    /** Finds a server in any list by its address (for a nicer name), or makes a plain one. */
    public Server find(String address) {
        for (List<Server> list : List.of(featured, partners, favourites, recent)) {
            for (Server server : list) {
                if (server.address().equalsIgnoreCase(address)) {
                    return server;
                }
            }
        }
        return new Server(address, address, "");
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new Saved(List.copyOf(favourites), List.copyOf(recent))));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
