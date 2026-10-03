package com.nexusclient.backend.party;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.friends.FriendService;
import com.nexusclient.backend.live.Event;
import com.nexusclient.backend.live.LiveHub;
import com.nexusclient.backend.live.PresenceChanged;
import com.nexusclient.backend.moderation.ChatFilter;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PartyService – parties: a leader plus friends playing together, with their own chat, shared
 * waypoints, and "warp" (the leader joins a server and everyone gets a one-click join).
 *
 * Parties only live in memory: they end when the backend restarts, like on most game servers.
 * Invites only go to friends (safety), expire after 60 seconds, and a party holds up to 8 players.
 * A player who goes offline leaves their party.
 */
@Service
public class PartyService {

    public static final int MAX_SIZE = 8;
    private static final long INVITE_SECONDS = 60;

    /** What the launcher/mod shows for a party. */
    public record PartyInfo(String id, UUID leader, List<Member> members) {}

    public record Member(UUID uuid, String name, boolean online, String server) {}

    private static final class Party {
        final String id = UUID.randomUUID().toString();
        volatile UUID leader;
        final Set<UUID> members = ConcurrentHashMap.newKeySet();
        final Map<UUID, Instant> invites = new ConcurrentHashMap<>();
    }

    private final Map<String, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partyOf = new ConcurrentHashMap<>();
    private final PlayerRepository players;
    private final FriendService friends;
    private final ChatFilter filter;
    private final LiveHub live;

    public PartyService(PlayerRepository players, FriendService friends, ChatFilter filter, LiveHub live) {
        this.players = players;
        this.friends = friends;
        this.filter = filter;
        this.live = live;
    }

    public synchronized PartyInfo current(UUID me) {
        Party party = partyOf.get(me);
        return party == null ? null : info(party);
    }

    /** Invites a friend (making a new party with you as leader if you're not in one). */
    public synchronized void invite(UUID me, UUID friend) {
        if (!friends.areFriends(me, friend)) {
            throw NexusException.forbidden("You can only invite friends");
        }
        if (!live.isOnline(friend)) {
            throw NexusException.badRequest("They're offline");
        }
        Party party = partyOf.get(me);
        if (party == null) {
            party = new Party();
            party.leader = me;
            party.members.add(me);
            parties.put(party.id, party);
            partyOf.put(me, party);
        }
        if (!party.leader.equals(me)) {
            throw NexusException.forbidden("Only the party leader can invite");
        }
        if (party.members.contains(friend)) {
            throw NexusException.badRequest("They're already in your party");
        }
        if (party.members.size() >= MAX_SIZE) {
            throw NexusException.badRequest("The party is full (" + MAX_SIZE + " players)");
        }
        party.invites.put(friend, Instant.now().plusSeconds(INVITE_SECONDS));
        live.send(friend, Event.of("party.invite").with("partyId", party.id).with("from", me).with("fromName", name(me)));
        broadcast(party, Event.of("party.update").withObject("party", info(party)));
    }

    public synchronized void accept(UUID me, String partyId) {
        Party party = parties.get(partyId);
        Instant expiry = party == null ? null : party.invites.remove(me);
        if (expiry == null || expiry.isBefore(Instant.now())) {
            throw NexusException.notFound("That invite has expired");
        }
        if (party.members.size() >= MAX_SIZE) {
            throw NexusException.badRequest("The party is full");
        }
        leave(me); // leave any other party first
        party.members.add(me);
        partyOf.put(me, party);
        broadcast(party, Event.of("party.update").withObject("party", info(party)));
    }

    public synchronized void leave(UUID me) {
        Party party = partyOf.remove(me);
        if (party == null) {
            return;
        }
        party.members.remove(me);
        live.send(me, Event.of("party.left").with("partyId", party.id));
        if (party.members.size() <= 1) {
            disband(party); // nobody left to play with
            return;
        }
        if (party.leader.equals(me)) {
            party.leader = party.members.iterator().next();
        }
        broadcast(party, Event.of("party.update").withObject("party", info(party)));
    }

    public synchronized void kick(UUID me, UUID member) {
        Party party = leaderParty(me);
        if (member.equals(me) || !party.members.contains(member)) {
            throw NexusException.badRequest("They're not in your party");
        }
        leave(member);
    }

    public synchronized void promote(UUID me, UUID member) {
        Party party = leaderParty(me);
        if (!party.members.contains(member)) {
            throw NexusException.badRequest("They're not in your party");
        }
        party.leader = member;
        broadcast(party, Event.of("party.update").withObject("party", info(party)));
    }

    /** Party chat (a separate chat channel just for the party). */
    public void chat(Player me, String text) {
        if (me.isMuted()) {
            throw NexusException.forbidden("You're muted until " + me.getMutedUntil());
        }
        Party party = memberParty(me.getUuid());
        broadcast(party, Event.of("party.chat").with("from", me.getUuid()).with("fromName", me.getName())
                .with("text", filter.clean(text.strip())));
    }

    /** Party waypoint: "look here" – shown to everyone in the party. */
    public void waypoint(UUID me, double x, double y, double z, String dimension, String label) {
        Party party = memberParty(me);
        broadcast(party, Event.of("party.waypoint").with("from", me).with("fromName", name(me))
                .with("x", x).with("y", y).with("z", z).with("dimension", dimension)
                .with("label", label == null ? "" : filter.clean(label)));
    }

    /** Warp: the leader asks everyone to join the server they're on. */
    public void warp(UUID me, String server) {
        Party party = leaderParty(me);
        Event event = Event.of("party.warp").with("server", server).with("from", me).with("fromName", name(me));
        for (UUID member : party.members) {
            if (!member.equals(me)) {
                live.send(member, event);
            }
        }
    }

    /** Someone in a party went offline → they leave it (so parties don't fill up with ghosts). */
    @EventListener
    public synchronized void presenceChanged(PresenceChanged change) {
        if (!change.online()) {
            leave(change.player());
        } else {
            Party party = partyOf.get(change.player());
            if (party != null) {
                broadcast(party, Event.of("party.update").withObject("party", info(party)));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Party memberParty(UUID me) {
        Party party = partyOf.get(me);
        if (party == null) {
            throw NexusException.badRequest("You're not in a party");
        }
        return party;
    }

    private Party leaderParty(UUID me) {
        Party party = memberParty(me);
        if (!party.leader.equals(me)) {
            throw NexusException.forbidden("Only the party leader can do that");
        }
        return party;
    }

    private void disband(Party party) {
        parties.remove(party.id);
        for (UUID member : new ArrayList<>(party.members)) {
            partyOf.remove(member, party);
            live.send(member, Event.of("party.left").with("partyId", party.id));
        }
        party.members.clear();
    }

    private void broadcast(Party party, Event event) {
        for (UUID member : party.members) {
            live.send(member, event);
        }
    }

    private PartyInfo info(Party party) {
        List<Member> members = new ArrayList<>();
        for (UUID uuid : new LinkedHashSet<>(party.members)) {
            Player p = players.findById(uuid).orElse(null);
            boolean online = live.isOnline(uuid);
            String server = p != null && p.isShowServer() ? live.serverOf(uuid) : "";
            members.add(new Member(uuid, p == null ? "?" : p.getName(), online, server));
        }
        return new PartyInfo(party.id, party.leader, members);
    }

    private String name(UUID uuid) {
        return players.findById(uuid).map(Player::getName).orElse("?");
    }
}
