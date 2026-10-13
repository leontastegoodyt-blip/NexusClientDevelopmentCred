package com.nexusclient.core.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusclient.core.NexusCore;
import com.nexusclient.core.map.Waypoints;
import com.nexusclient.core.module.Category;
import com.nexusclient.core.module.Module;
import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.ui.Toasts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Social – Nexus friends, party and clan inside the game (the project brief section 6).
 *
 *   - Pop-ups when a friend comes online, sends a friend request or a party invite (ui/Toasts).
 *   - Party chat: type "@p hello" in chat – only your party sees it. Clan chat: "@c hello".
 *     Their messages show in chat as [Party] / [Clan] lines (never sent to the Minecraft server).
 *   - Party pings: press G to mark the block you look at; everyone in your party sees a marker there
 *     (in the world and on the minimap) for a minute.
 *   - Clan tags after names on name tags (fetched with the badges, see Badges).
 *   - Emotes: press V to play the emote you wear (Wave) – Nexus players on your server see it (Emotes).
 *   - Your server is shared with friends ("presence"), so they can join you from the launcher.
 *   - The Right Shift menu's Social tab shows all of it (ModMenu).
 *
 * It keeps a live connection to the Nexus server (LiveSocket) and sends actions through the normal
 * web API (NexusHttp). Only works when the game was started by the Nexus launcher while signed in.
 */
public class Social extends Module implements com.nexusclient.core.hud.Overlay {

    public static final String PARTY_PREFIX = "@p ";
    public static final String CLAN_PREFIX = "@c ";
    private static final int PARTY_COLOUR = 0xB98CFF;
    private static final int CLAN_COLOUR = 0x5FD3FF;
    private static final int FRIEND_COLOUR = 0x5BE49B;

    public final BooleanSetting popups = add(new BooleanSetting("popups", "Pop-ups (invites, messages)", true));
    public final BooleanSetting friendOnline = add(new BooleanSetting("friendOnline", "Pop-up when a friend comes online", true));
    public final BooleanSetting clanTags = add(new BooleanSetting("clanTags", "Clan tags on name tags", true));
    public final BooleanSetting partyPings = add(new BooleanSetting("partyPings", "Show party pings", true));

    // ------------------------------------------------------------------ what we know

    /** A friend or party member. */
    public static final class Person {
        public final UUID uuid;
        public final String name;
        public final boolean online;
        public final String server;

        Person(UUID uuid, String name, boolean online, String server) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
            this.server = server == null ? "" : server;
        }
    }

    /** One chat message in the party or clan channel. */
    public static final class ChatLine {
        public final String from;
        public final String text;
        public final long time = System.currentTimeMillis();

        ChatLine(String from, String text) {
            this.from = from;
            this.text = text;
        }
    }

    /** A party invite waiting for an answer. */
    public static final class Invite {
        public final String partyId;
        public final String fromName;
        public final long time = System.currentTimeMillis();

        Invite(String partyId, String fromName) {
            this.partyId = partyId;
            this.fromName = fromName;
        }
    }

    private final List<Person> friends = new ArrayList<Person>();
    private final List<Person> requests = new ArrayList<Person>();
    private final List<Person> partyMembers = new ArrayList<Person>();
    private final List<Invite> invites = new ArrayList<Invite>();
    private final List<ChatLine> partyChat = new ArrayList<ChatLine>();
    private final List<ChatLine> clanChat = new ArrayList<ChatLine>();
    private volatile String partyId;
    private volatile UUID partyLeader;
    private volatile long clanId = -1;
    private volatile String clanName = "";
    private volatile String clanTag = "";
    private volatile String status = "Offline";

    private LiveSocket socket;
    private String server = "";
    private int ticks;

    public Social() {
        super("social", "Nexus Social",
                "Friends, party and clan in-game: pop-ups, @p party chat, @c clan chat, G to ping.",
                Category.UTILITY, true, false);
    }

    // ------------------------------------------------------------------ running

    /**
     * Called every game tick by NexusCore (even when switched off, so switching it off disconnects).
     * Starts/stops the live connection and keeps it alive.
     */
    public void update(GameInfo game) {
        boolean wanted = isActive() && NexusHttp.signedIn();
        if (wanted && socket == null) {
            status = "Connecting…";
            socket = new LiveSocket(NexusHttp.BACKEND, NexusHttp.TOKEN, new LiveSocket.Listener() {
                public void connected() {
                    status = "Online";
                    sendPresence();
                    refreshAll();
                }

                public void message(String json) {
                    handle(json);
                }

                public void disconnected() {
                    status = "Reconnecting…";
                }
            });
            socket.start();
        } else if (!wanted && socket != null) {
            socket.stop();
            socket = null;
            status = NexusHttp.signedIn() ? "Switched off" : "Offline";
        }
        if (socket != null && ++ticks % 500 == 0) { // every 25 seconds: "still here"
            final LiveSocket live = socket;
            NexusHttp.async(new Runnable() {
                public void run() {
                    live.send("{\"type\":\"ping\"}");
                }
            });
        }
    }

    /**
     * The emote key was pressed: ask the Nexus server to play our emote (it tells everyone on our
     * Minecraft server, us included). Developer self-test: plays the wave straight away.
     */
    public void emote(com.nexusclient.core.platform.GameInfo game) {
        if (System.getProperty("nexus.cosmeticTest") != null) {
            Emotes.play(game.playerUuid(), "wave");
            return;
        }
        final LiveSocket live = socket;
        if (live == null || !live.isOpen()) {
            Toasts.show("Emotes need Nexus Social", "Sign in to Nexus and switch on Nexus Social", "!", FRIEND_COLOUR);
            return;
        }
        if (server.isEmpty()) {
            Toasts.show("Emotes", "Join a server to show your emote to other Nexus players", "!", FRIEND_COLOUR);
            return;
        }
        NexusHttp.async(new Runnable() {
            public void run() {
                live.send("{\"type\":\"emote\"}");
            }
        });
    }

    /** The player joined a server ("" = singleplayer / menu). Friends see where we are. */
    public void serverChanged(String address) {
        server = address == null ? "" : address;
        sendPresence();
    }

    private void sendPresence() {
        final LiveSocket live = socket;
        if (live == null) {
            return;
        }
        final JsonObject presence = new JsonObject();
        presence.addProperty("type", "presence");
        presence.addProperty("server", server);
        NexusHttp.async(new Runnable() {
            public void run() {
                live.send(presence.toString());
            }
        });
    }

    // ------------------------------------------------------------------ reading what the server says

    private void refreshAll() {
        NexusHttp.async(new Runnable() {
            public void run() {
                refreshFriends();
                refreshParty();
                refreshClan();
            }
        });
    }

    private void refreshFriends() {
        try {
            JsonObject overview = NexusHttp.get("/api/friends").getAsJsonObject();
            List<Person> fresh = new ArrayList<Person>();
            for (JsonElement element : overview.getAsJsonArray("friends")) {
                fresh.add(person(element.getAsJsonObject()));
            }
            List<Person> incoming = new ArrayList<Person>();
            for (JsonElement element : overview.getAsJsonArray("incoming")) {
                incoming.add(person(element.getAsJsonObject()));
            }
            synchronized (this) {
                friends.clear();
                friends.addAll(fresh);
                requests.clear();
                requests.addAll(incoming);
            }
        } catch (Exception e) {
            // offline – the lists stay as they were
        }
    }

    private void refreshParty() {
        try {
            JsonElement party = NexusHttp.get("/api/party").getAsJsonObject().get("party");
            setParty(party == null || party.isJsonNull() ? null : party.getAsJsonObject());
        } catch (Exception e) {
            // offline
        }
    }

    private void refreshClan() {
        try {
            JsonArray clans = NexusHttp.get("/api/clans/mine").getAsJsonArray();
            JsonObject chosen = null;
            for (JsonElement element : clans) {
                JsonObject clan = element.getAsJsonObject();
                if (chosen == null || (clan.has("primary") && clan.get("primary").getAsBoolean())) {
                    chosen = clan;
                }
            }
            if (chosen == null) {
                clanId = -1;
                clanName = "";
                clanTag = "";
            } else {
                clanId = chosen.get("id").getAsLong();
                clanName = chosen.get("name").getAsString();
                clanTag = chosen.get("tag").getAsString();
            }
        } catch (Exception e) {
            // offline
        }
    }

    private void setParty(JsonObject party) {
        synchronized (this) {
            partyMembers.clear();
            if (party == null) {
                partyId = null;
                partyLeader = null;
                return;
            }
            partyId = party.get("id").getAsString();
            partyLeader = UUID.fromString(party.get("leader").getAsString());
            for (JsonElement element : party.getAsJsonArray("members")) {
                partyMembers.add(person(element.getAsJsonObject()));
            }
        }
    }

    /** One live event from the server. Runs on the live connection's thread. */
    private void handle(String json) {
        JsonObject event = new JsonParser().parse(json).getAsJsonObject();
        String type = event.has("type") ? event.get("type").getAsString() : "";
        boolean showPopups = isActive() && popups.isOn();
        if (type.equals("friend.presence")) {
            Person friend = person(event.getAsJsonObject("friend"));
            boolean cameOnline = false;
            synchronized (this) {
                Person old = find(friends, friend.uuid);
                cameOnline = friend.online && (old == null || !old.online);
                replace(friends, friend);
            }
            if (cameOnline && showPopups && friendOnline.isOn()) {
                Toasts.show(friend.name + " is online", friend.server.isEmpty() ? "In the Nexus launcher" : "Playing on " + friend.server,
                        "F", FRIEND_COLOUR);
            }
        } else if (type.equals("friend.request")) {
            Person from = new Person(uuid(event, "uuid"), str(event, "name"), false, "");
            synchronized (this) {
                replace(requests, from);
            }
            if (showPopups) {
                Toasts.show("Friend request", from.name + " · accept in Right Shift → Social", "+", FRIEND_COLOUR);
            }
        } else if (type.equals("friend.accepted")) {
            Person friend = person(event.getAsJsonObject("friend"));
            synchronized (this) {
                replace(friends, friend);
                remove(requests, friend.uuid);
            }
            if (showPopups) {
                Toasts.show("New friend", friend.name + " is now your friend", "F", FRIEND_COLOUR);
            }
        } else if (type.equals("friend.removed")) {
            synchronized (this) {
                remove(friends, uuid(event, "uuid"));
            }
        } else if (type.equals("emote")) {
            Emotes.play(uuid(event, "player"), str(event, "emote")); // a player on our server waves
        } else if (type.equals("dm")) {
            String from = str(event, "fromName");
            JsonObject message = event.getAsJsonObject("message");
            String text = message != null && message.has("text") ? message.get("text").getAsString() : "";
            chatLine("DM", 0xFFB86B, from, text);
            if (showPopups) {
                Toasts.show("Message from " + from, text, "@", 0xFFB86B);
            }
        } else if (type.equals("party.invite")) {
            Invite invite = new Invite(str(event, "partyId"), str(event, "fromName"));
            synchronized (this) {
                invites.add(0, invite);
                if (invites.size() > 5) {
                    invites.remove(invites.size() - 1);
                }
            }
            if (showPopups) {
                Toasts.show("Party invite", invite.fromName + " · accept in Right Shift → Social", "P", PARTY_COLOUR);
            }
        } else if (type.equals("party.update")) {
            setParty(event.getAsJsonObject("party"));
        } else if (type.equals("party.left")) {
            setParty(null);
            if (showPopups) {
                Toasts.show("Party", "You're no longer in a party", "P", PARTY_COLOUR);
            }
        } else if (type.equals("party.chat")) {
            String from = str(event, "fromName");
            String text = str(event, "text");
            addLine(partyChat, new ChatLine(from, text));
            chatLine("Party", PARTY_COLOUR, from, text);
        } else if (type.equals("party.waypoint")) {
            receivePing(event);
        } else if (type.equals("party.warp")) {
            chatLine("Party", PARTY_COLOUR, str(event, "fromName"), "wants everyone on " + str(event, "server"));
            if (showPopups) {
                Toasts.show("Party warp", str(event, "fromName") + " → " + str(event, "server"), "P", PARTY_COLOUR);
            }
        } else if (type.equals("clan.chat")) {
            JsonObject message = event.getAsJsonObject("message");
            String from = message.get("fromName").getAsString();
            String text = message.get("text").getAsString();
            addLine(clanChat, new ChatLine(from, text));
            chatLine(clanTag.isEmpty() ? "Clan" : clanTag, CLAN_COLOUR, from, text);
        } else if (type.equals("clan.invite")) {
            if (showPopups) {
                Toasts.show("Clan invite", str(event, "clanName") + " · answer in the launcher", "C", CLAN_COLOUR);
            }
        } else if (type.startsWith("clan.")) {
            NexusHttp.async(new Runnable() {
                public void run() {
                    refreshClan();
                }
            });
        } else if (type.equals("notice") || type.equals("kicked") || type.equals("banned")) {
            String text = event.has("text") ? str(event, "text")
                    : type.equals("kicked") ? "You were signed out of Nexus by staff." : "Your Nexus account was banned.";
            NexusCore.get().noticeLater(text);
        }
    }

    /** Party ping markers in the world (drawn even when the Waypoints mod is off). */
    @Override
    public void drawOverlay(com.nexusclient.core.platform.Renderer renderer, GameInfo game) {
        if (partyPings.isOn()) {
            Waypoints waypoints = NexusCore.get().get(Waypoints.class);
            waypoints.drawMarkers(renderer, game, waypoints.pings(game), 100000);
        }
    }

    // ------------------------------------------------------------------ doing things

    /**
     * Chat typed by the player. Returns true if it was party/clan chat (so it must NOT go to the
     * Minecraft server).
     */
    public boolean handleChatInput(String message) {
        if (!isActive() || !NexusHttp.signedIn() || message == null) {
            return false;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith(PARTY_PREFIX) && message.trim().length() > 2) {
            sendPartyChat(message.substring(PARTY_PREFIX.length()).trim());
            return true;
        }
        if (lower.startsWith(CLAN_PREFIX) && message.trim().length() > 2) {
            sendClanChat(message.substring(CLAN_PREFIX.length()).trim());
            return true;
        }
        return false;
    }

    public void sendPartyChat(String text) {
        if (text.isEmpty()) {
            return;
        }
        if (partyId == null) {
            NexusCore.get().noticeLater("You're not in a party. Invite friends from Right Shift → Social.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("text", text.length() > 256 ? text.substring(0, 256) : text);
        call("/api/party/chat", body, null);
    }

    public void sendClanChat(String text) {
        if (text.isEmpty()) {
            return;
        }
        if (clanId < 0) {
            NexusCore.get().noticeLater("You're not in a clan. Join or make one in the launcher's Clans tab.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("text", text.length() > 500 ? text.substring(0, 500) : text);
        call("/api/clans/" + clanId + "/chat", body, null);
    }

    /** The party ping key: marks the block you look at (or where you stand) for the whole party. */
    public void ping(GameInfo game) {
        if (!game.inWorld() || !isActive()) {
            return;
        }
        int[] block = game.lookedAtBlock();
        double x = block != null ? block[0] : Math.floor(game.x());
        double y = block != null ? block[1] : Math.floor(game.y());
        double z = block != null ? block[2] : Math.floor(game.z());
        if (partyId == null || !NexusHttp.signedIn()) {
            // Not in a party: still show it to yourself, so the key does something.
            NexusCore.get().get(Waypoints.class).addPing(game.playerName(), PARTY_COLOUR, x, y, z, game.dimension(), 60000);
            NexusCore.get().noticeLater("Pinged. Join a party (Right Shift → Social) to share pings.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("x", x);
        body.addProperty("y", y);
        body.addProperty("z", z);
        body.addProperty("dimension", game.dimension());
        body.addProperty("label", "");
        call("/api/party/waypoint", body, null); // the server sends it back to everyone, us included
    }

    private void receivePing(JsonObject event) {
        if (!isActive() || !partyPings.isOn()) {
            return;
        }
        final String from = str(event, "fromName");
        final double x = event.get("x").getAsDouble();
        final double y = event.get("y").getAsDouble();
        final double z = event.get("z").getAsDouble();
        final String dimension = str(event, "dimension");
        NexusCore.get().runLater(new Runnable() {
            public void run() {
                NexusCore.get().get(Waypoints.class).addPing(from, PARTY_COLOUR, x, y, z, dimension, 60000);
            }
        });
        chatLine("Party", PARTY_COLOUR, from, "pinged " + (int) x + " " + (int) y + " " + (int) z);
    }

    public void acceptInvite(final Invite invite) {
        JsonObject body = new JsonObject();
        body.addProperty("partyId", invite.partyId);
        synchronized (this) {
            invites.remove(invite);
        }
        call("/api/party/accept", body, "Joined " + invite.fromName + "'s party.");
    }

    public void declineInvite(Invite invite) {
        synchronized (this) {
            invites.remove(invite);
        }
    }

    public void inviteToParty(Person friend) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", friend.uuid.toString());
        call("/api/party/invite", body, "Invited " + friend.name + " to your party.");
    }

    public void leaveParty() {
        call("/api/party/leave", null, "You left the party.");
    }

    public void acceptFriend(Person person) {
        answerRequest(person, "/api/friends/accept");
    }

    public void declineFriend(Person person) {
        answerRequest(person, "/api/friends/decline");
    }

    private void answerRequest(Person person, String path) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", person.uuid.toString());
        synchronized (this) {
            remove(requests, person.uuid);
        }
        call(path, body, null);
        NexusHttp.async(new Runnable() {
            public void run() {
                refreshFriends();
            }
        });
    }

    /** Sends an action in the background; shows the server's reason if it says no. */
    private void call(final String path, final JsonObject body, final String done) {
        if (!NexusHttp.signedIn()) {
            NexusCore.get().noticeLater("Start Minecraft from the Nexus launcher while signed in to use Nexus Social.");
            return;
        }
        NexusHttp.async(new Runnable() {
            public void run() {
                try {
                    NexusHttp.post(path, body);
                    if (done != null) {
                        NexusCore.get().noticeLater(done);
                    }
                } catch (Exception e) {
                    NexusCore.get().noticeLater(e.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------------ for the Social tab

    public String status() {
        return NexusHttp.signedIn() ? status : "Start the game from the Nexus launcher while signed in";
    }

    public boolean online() {
        return socket != null && socket.isOpen();
    }

    public synchronized List<Person> friends() {
        List<Person> sorted = new ArrayList<Person>(friends);
        Collections.sort(sorted, new java.util.Comparator<Person>() {
            public int compare(Person a, Person b) {
                if (a.online != b.online) {
                    return a.online ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return sorted;
    }

    public synchronized List<Person> requests() {
        return new ArrayList<Person>(requests);
    }

    public synchronized List<Person> partyMembers() {
        return new ArrayList<Person>(partyMembers);
    }

    public synchronized List<Invite> invites() {
        return new ArrayList<Invite>(invites);
    }

    public synchronized List<ChatLine> partyChat() {
        return new ArrayList<ChatLine>(partyChat);
    }

    public synchronized List<ChatLine> clanChat() {
        return new ArrayList<ChatLine>(clanChat);
    }

    public boolean inParty() {
        return partyId != null;
    }

    public UUID partyLeader() {
        return partyLeader;
    }

    public String clanName() {
        return clanName;
    }

    public String clanTag() {
        return clanTag;
    }

    public boolean inClan() {
        return clanId >= 0;
    }

    // ------------------------------------------------------------------ small helpers

    /** Shows "[Party] Name: text" in chat on the game thread (only on this player's screen). */
    private void chatLine(final String channel, final int colour, final String from, final String text) {
        if (!isActive()) {
            return;
        }
        NexusCore.get().runLater(new Runnable() {
            public void run() {
                NexusCore.get().platform().showChatLine("[" + channel + "] ", colour, from + ": " + text);
            }
        });
    }

    private synchronized void addLine(List<ChatLine> list, ChatLine line) {
        list.add(line);
        while (list.size() > 60) {
            list.remove(0);
        }
    }

    private static Person person(JsonObject json) {
        return new Person(UUID.fromString(json.get("uuid").getAsString()), json.get("name").getAsString(),
                json.has("online") && json.get("online").getAsBoolean(),
                json.has("server") && !json.get("server").isJsonNull() ? json.get("server").getAsString() : "");
    }

    private static Person find(List<Person> list, UUID uuid) {
        for (Person person : list) {
            if (person.uuid.equals(uuid)) {
                return person;
            }
        }
        return null;
    }

    private static void replace(List<Person> list, Person person) {
        remove(list, person.uuid);
        list.add(person);
    }

    private static void remove(List<Person> list, UUID uuid) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).uuid.equals(uuid)) {
                list.remove(i);
            }
        }
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static UUID uuid(JsonObject json, String key) {
        return UUID.fromString(str(json, key));
    }
}
