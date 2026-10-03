package com.nexusclient.backend.friends;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.live.Event;
import com.nexusclient.backend.live.LiveHub;
import com.nexusclient.backend.live.PresenceChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FriendService – friend requests, the friends list (with who's online and where) and blocking.
 * Friends see which server you're on unless you switch "show my server" off (privacy toggle).
 */
@Service
public class FriendService {

    public static final int MAX_FRIENDS = 200;
    public static final int MAX_OUTGOING_REQUESTS = 50;

    /** A friend as the launcher/mod shows them. server is "" when hidden, offline or not on a server. */
    public record FriendInfo(UUID uuid, String name, boolean online, String server, String since) {}

    /** Someone in a request or block list. */
    public record PlayerRef(UUID uuid, String name) {}

    /** Everything for the Friends screen in one go. */
    public record Overview(List<FriendInfo> friends, List<PlayerRef> incoming, List<PlayerRef> outgoing,
                           List<PlayerRef> blocked) {}

    private final PlayerRepository players;
    private final FriendRequestRepository requests;
    private final FriendshipRepository friendships;
    private final BlockRepository blocks;
    private final LiveHub live;

    public FriendService(PlayerRepository players, FriendRequestRepository requests, FriendshipRepository friendships,
                         BlockRepository blocks, LiveHub live) {
        this.players = players;
        this.requests = requests;
        this.friendships = friendships;
        this.blocks = blocks;
        this.live = live;
    }

    // ------------------------------------------------------------------ asking

    public boolean areFriends(UUID a, UUID b) {
        return friendships.existsByPlayerAndFriend(a, b);
    }

    public boolean eitherBlocked(UUID a, UUID b) {
        return blocks.eitherBlocked(a, b);
    }

    public List<UUID> friendsOf(UUID player) {
        return friendships.findByPlayerOrderBySinceAsc(player).stream().map(Friendship::getFriend).toList();
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID me) {
        List<Friendship> mine = friendships.findByPlayerOrderBySinceAsc(me);
        Map<UUID, Player> known = playersById(mine.stream().map(Friendship::getFriend).toList());
        List<FriendInfo> friends = new ArrayList<>();
        for (Friendship f : mine) {
            Player friend = known.get(f.getFriend());
            if (friend != null) {
                friends.add(info(friend, f.getSince().toString()));
            }
        }
        // Online friends first, then by name.
        friends.sort((x, y) -> x.online() != y.online() ? (x.online() ? -1 : 1) : x.name().compareToIgnoreCase(y.name()));
        return new Overview(friends,
                refs(requests.findByToOrderByCreatedAtDesc(me).stream().map(FriendRequest::getFrom).toList()),
                refs(requests.findByFromOrderByCreatedAtDesc(me).stream().map(FriendRequest::getTo).toList()),
                refs(blocks.findByBlocker(me).stream().map(Block::getBlocked).toList()));
    }

    // ------------------------------------------------------------------ doing

    /** Sends a friend request by name (they must have used Nexus). If they already asked us, it's accepted. */
    @Transactional
    public String request(UUID me, String name) {
        Player target = players.findFirstByNameIgnoreCaseOrderByLastSeenDesc(name.trim())
                .orElseThrow(() -> NexusException.notFound(name + " hasn't used Nexus yet"));
        UUID them = target.getUuid();
        if (them.equals(me)) {
            throw NexusException.badRequest("You can't add yourself");
        }
        if (eitherBlocked(me, them)) {
            throw NexusException.forbidden("You can't add " + target.getName());
        }
        if (areFriends(me, them)) {
            throw NexusException.badRequest("You're already friends with " + target.getName());
        }
        if (requests.findByFromAndTo(them, me).isPresent()) {
            accept(me, them); // they already asked us – just become friends
            return "accepted";
        }
        if (requests.findByFromAndTo(me, them).isPresent()) {
            throw NexusException.badRequest("You already sent " + target.getName() + " a request");
        }
        if (requests.countByFrom(me) >= MAX_OUTGOING_REQUESTS) {
            throw NexusException.badRequest("Too many requests waiting – wait for some answers first");
        }
        checkRoom(me);
        requests.save(new FriendRequest(me, them));
        Player self = players.findById(me).orElseThrow();
        live.send(them, Event.of("friend.request").with("uuid", me).with("name", self.getName()));
        return "sent";
    }

    @Transactional
    public void accept(UUID me, UUID from) {
        requests.findByFromAndTo(from, me)
                .orElseThrow(() -> NexusException.notFound("That friend request isn't there any more"));
        checkRoom(me);
        checkRoom(from);
        requests.deleteBetween(me, from);
        friendships.save(new Friendship(me, from));
        friendships.save(new Friendship(from, me));
        Player self = players.findById(me).orElseThrow();
        Player other = players.findById(from).orElseThrow();
        live.send(from, Event.of("friend.accepted").withObject("friend", info(self, null)));
        live.send(me, Event.of("friend.accepted").withObject("friend", info(other, null)));
    }

    /** Says no to a request they sent us, or takes back one we sent. */
    @Transactional
    public void decline(UUID me, UUID other) {
        requests.deleteBetween(me, other);
    }

    @Transactional
    public void remove(UUID me, UUID friend) {
        if (friendships.deleteBetween(me, friend) > 0) {
            live.send(friend, Event.of("friend.removed").with("uuid", me));
            live.send(me, Event.of("friend.removed").with("uuid", friend));
        }
    }

    /** Blocking also ends any friendship and requests between the two players. */
    @Transactional
    public void block(UUID me, UUID target) {
        if (me.equals(target)) {
            throw NexusException.badRequest("You can't block yourself");
        }
        if (!players.existsById(target)) {
            throw NexusException.notFound("That player hasn't used Nexus");
        }
        remove(me, target);
        requests.deleteBetween(me, target);
        blocks.save(new Block(me, target));
    }

    @Transactional
    public void unblock(UUID me, UUID target) {
        blocks.deleteById(new Block.Key(me, target));
    }

    // ------------------------------------------------------------------ live status

    /** A player came online, went offline or changed server – tell their online friends. */
    @EventListener
    public void presenceChanged(PresenceChanged change) {
        Player player = players.findById(change.player()).orElse(null);
        if (player == null) {
            return;
        }
        Event event = Event.of("friend.presence").withObject("friend", info(player, null));
        for (UUID friend : friendsOf(change.player())) {
            live.send(friend, event);
        }
        if (change.online()) {
            player.seenNow();
            players.save(player);
        }
    }

    // ------------------------------------------------------------------ helpers

    private FriendInfo info(Player player, String since) {
        boolean online = live.isOnline(player.getUuid());
        String server = online && player.isShowServer() ? live.serverOf(player.getUuid()) : "";
        return new FriendInfo(player.getUuid(), player.getName(), online, server, since);
    }

    private void checkRoom(UUID player) {
        if (friendships.countByPlayer(player) >= MAX_FRIENDS) {
            throw NexusException.badRequest("Friends list is full (" + MAX_FRIENDS + " friends)");
        }
    }

    private Map<UUID, Player> playersById(List<UUID> ids) {
        return players.findAllById(ids).stream().collect(Collectors.toMap(Player::getUuid, Function.identity()));
    }

    private List<PlayerRef> refs(List<UUID> ids) {
        Map<UUID, Player> known = playersById(ids);
        return ids.stream().filter(known::containsKey)
                .map(id -> new PlayerRef(id, known.get(id).getName())).toList();
    }
}
