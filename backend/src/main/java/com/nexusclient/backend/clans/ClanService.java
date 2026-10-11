package com.nexusclient.backend.clans;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.live.Event;
import com.nexusclient.backend.live.LiveHub;
import com.nexusclient.backend.moderation.ChatFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ClanService – everything about clans (the project brief section 6, Clans): creating, inviting, ranks,
 * kicking, clan chat, polls, the leaderboard and the podium (top 3 clans get Nexus Premium free).
 *
 * Limits: a player can create 1 clan (2 with Premium) and be in 1 clan (2 with Premium); 35 members
 * per clan; rank limits are in ClanRank. A player's honor goes to their "primary" clan.
 */
@Service
public class ClanService {

    private static final String NAME = "[A-Za-z0-9 _\\-]{3,24}";
    private static final String TAG = "[A-Za-z0-9]{2,5}";

    // ------------------------------------------------------------------ what the launcher sees

    public record MemberView(UUID uuid, String name, ClanRank rank, long honor, boolean online) {}

    public record ClanView(long id, String name, String tag, String description, long honor, boolean mvp,
                           int place, boolean podium, ClanRank myRank, boolean primary, List<MemberView> members,
                           List<String> invited) {}

    public record ClanSummary(long id, String name, String tag, long honor, boolean mvp, int place, boolean podium,
                              long members) {}

    public record InviteView(long clanId, String clanName, String tag) {}

    public record MessageView(long id, UUID from, String fromName, String text, String sentAt) {}

    public record PollView(long id, String question, List<String> options, List<Integer> votes, Integer myVote,
                           boolean open, String endsAt) {}

    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final ClanInviteRepository invites;
    private final ClanMessageRepository messages;
    private final ClanPollRepository polls;
    private final ClanVoteRepository votes;
    private final PlayerRepository players;
    private final ChatFilter filter;
    private final LiveHub live;

    public ClanService(ClanRepository clans, ClanMemberRepository members, ClanInviteRepository invites,
                       ClanMessageRepository messages, ClanPollRepository polls, ClanVoteRepository votes,
                       PlayerRepository players, ChatFilter filter, LiveHub live) {
        this.clans = clans;
        this.members = members;
        this.invites = invites;
        this.messages = messages;
        this.polls = polls;
        this.votes = votes;
        this.players = players;
        this.filter = filter;
        this.live = live;
    }

    // ------------------------------------------------------------------ premium and the podium

    /** Premium = bought it, or a member of a top-3 clan (for as long as the clan stays there). */
    @Transactional(readOnly = true)
    public boolean isPremium(UUID player) {
        Player p = players.findById(player).orElse(null);
        if (p != null && p.hasPaidPremium()) {
            return true;
        }
        Set<Long> podium = clans.podium().stream().map(Clan::getId).collect(Collectors.toSet());
        return members.findByPlayer(player).stream().anyMatch(m -> podium.contains(m.getClanId()));
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<ClanView> myClans(UUID me) {
        Player player = players.findById(me).orElseThrow();
        List<ClanView> views = new ArrayList<>();
        for (ClanMember membership : members.findByPlayer(me)) {
            clans.findById(membership.getClanId()).ifPresent(clan ->
                    views.add(view(clan, membership.getRank(), clan.getId().equals(player.getPrimaryClanId()))));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public List<InviteView> myInvites(UUID me) {
        List<InviteView> list = new ArrayList<>();
        for (ClanInvite invite : invites.findByPlayer(me)) {
            clans.findById(invite.getClanId()).ifPresent(c -> list.add(new InviteView(c.getId(), c.getName(), c.getTag())));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<ClanSummary> leaderboard() {
        return summaries(clans.leaderboard());
    }

    @Transactional(readOnly = true)
    public List<ClanSummary> search(String text) {
        return summaries(clans.findTop30ByNameContainingIgnoreCaseOrderByHonorDesc(text.trim()));
    }

    /** Clan tags for a list of players (for showing [TAG] next to names in-game). */
    @Transactional(readOnly = true)
    public Map<String, String> tags(Collection<UUID> uuids) {
        Map<String, String> tags = new HashMap<>();
        Map<UUID, Player> known = players.findAllById(uuids).stream()
                .collect(Collectors.toMap(Player::getUuid, Function.identity()));
        for (Player p : known.values()) {
            if (p.getPrimaryClanId() != null) {
                clans.findById(p.getPrimaryClanId()).ifPresent(c -> tags.put(p.getUuid().toString(), c.getTag()));
            }
        }
        return tags;
    }

    // ------------------------------------------------------------------ creating and joining

    @Transactional
    public ClanView create(UUID me, String name, String tag) {
        name = name.trim();
        if (!name.matches(NAME)) {
            throw NexusException.badRequest("Clan names are 3-24 letters, numbers, spaces, - or _");
        }
        if (!tag.matches(TAG)) {
            throw NexusException.badRequest("Clan tags are 2-5 letters or numbers");
        }
        boolean premium = isPremium(me);
        int limit = premium ? 2 : 1;
        if (clans.countByOwner(me) >= limit) {
            throw NexusException.badRequest(premium ? "You already own 2 clans"
                    : "You can own 1 clan (2 with Nexus Premium)");
        }
        if (members.countByPlayer(me) >= limit) {
            throw NexusException.badRequest("Leave a clan first (you can be in " + limit + ")");
        }
        if (clans.nameTaken(name)) {
            throw NexusException.badRequest("That clan name is taken");
        }
        if (clans.tagTaken(tag)) {
            throw NexusException.badRequest("That clan tag is taken");
        }
        Clan clan = clans.save(new Clan(filter.clean(name), tag.toUpperCase(), me));
        if (clan.getName().contains("*")) {
            throw NexusException.badRequest("Please pick a different name");
        }
        members.save(new ClanMember(clan.getId(), me, ClanRank.OWNER));
        makePrimaryIfNone(me, clan.getId());
        return view(clan, ClanRank.OWNER, true);
    }

    @Transactional
    public void invite(UUID me, long clanId, String name) {
        ClanMember mine = membership(me, clanId);
        if (!mine.getRank().canInviteAndKick()) {
            throw NexusException.forbidden("Your rank can't invite players");
        }
        Player target = players.findFirstByNameIgnoreCaseOrderByLastSeenDesc(name.trim())
                .orElseThrow(() -> NexusException.notFound(name + " hasn't used Nexus yet"));
        if (members.existsById(new ClanMember.Key(clanId, target.getUuid()))) {
            throw NexusException.badRequest(target.getName() + " is already in the clan");
        }
        if (members.countByClanId(clanId) >= Clan.MAX_MEMBERS) {
            throw NexusException.badRequest("The clan is full (" + Clan.MAX_MEMBERS + " members)");
        }
        Clan clan = clans.findById(clanId).orElseThrow();
        invites.save(new ClanInvite(clanId, target.getUuid(), me));
        live.send(target.getUuid(), Event.of("clan.invite").with("clanId", clanId).with("clanName", clan.getName())
                .with("tag", clan.getTag()));
    }

    @Transactional
    public void acceptInvite(UUID me, long clanId) {
        ClanInvite.Key key = new ClanInvite.Key(clanId, me);
        if (!invites.existsById(key)) {
            throw NexusException.notFound("That invite isn't there any more");
        }
        int limit = isPremium(me) ? 2 : 1;
        if (members.countByPlayer(me) >= limit) {
            throw NexusException.badRequest("Leave your clan first (you can be in " + limit + ")");
        }
        if (members.countByClanId(clanId) >= Clan.MAX_MEMBERS) {
            throw NexusException.badRequest("The clan is full");
        }
        invites.deleteById(key);
        members.save(new ClanMember(clanId, me, ClanRank.MEMBER));
        makePrimaryIfNone(me, clanId);
        broadcast(clanId, Event.of("clan.update").with("clanId", clanId));
    }

    @Transactional
    public void declineInvite(UUID me, long clanId) {
        invites.deleteById(new ClanInvite.Key(clanId, me));
    }

    @Transactional
    public void leave(UUID me, long clanId) {
        ClanMember mine = membership(me, clanId);
        if (mine.getRank() == ClanRank.OWNER) {
            throw NexusException.badRequest("Owners can't leave: give the clan to someone else or delete it");
        }
        removeMember(clanId, me);
    }

    @Transactional
    public void kick(UUID me, long clanId, UUID target) {
        ClanMember mine = membership(me, clanId);
        ClanMember theirs = membership(target, clanId);
        if (!mine.getRank().canInviteAndKick() || !mine.getRank().isAbove(theirs.getRank())) {
            throw NexusException.forbidden("You can't kick this member");
        }
        removeMember(clanId, target);
        live.send(target, Event.of("clan.kicked").with("clanId", clanId));
    }

    @Transactional
    public void setRank(UUID me, long clanId, UUID target, ClanRank rank) {
        ClanMember mine = membership(me, clanId);
        ClanMember theirs = membership(target, clanId);
        if (rank == ClanRank.OWNER) {
            throw NexusException.badRequest("Use \"Give clan\" to make someone the owner");
        }
        if (!mine.getRank().canSetRanks() || !mine.getRank().isAbove(theirs.getRank()) || !mine.getRank().isAbove(rank)) {
            throw NexusException.forbidden("You can't give that rank");
        }
        if (members.countByClanIdAndRank(clanId, rank) >= rank.limit) {
            throw NexusException.badRequest("The clan already has the most " + rank.name().replace('_', ' ').toLowerCase()
                    + "s allowed (" + rank.limit + ")");
        }
        theirs.setRank(rank);
        members.save(theirs);
        broadcast(clanId, Event.of("clan.update").with("clanId", clanId));
    }

    /** The owner gives the clan to another member (they swap ranks). */
    @Transactional
    public void transfer(UUID me, long clanId, UUID target) {
        ClanMember mine = membership(me, clanId);
        ClanMember theirs = membership(target, clanId);
        if (mine.getRank() != ClanRank.OWNER) {
            throw NexusException.forbidden("Only the owner can give the clan away");
        }
        mine.setRank(theirs.getRank());
        theirs.setRank(ClanRank.OWNER);
        members.save(mine);
        members.save(theirs);
        Clan clan = clans.findById(clanId).orElseThrow();
        clan.setOwner(target);
        clans.save(clan);
        broadcast(clanId, Event.of("clan.update").with("clanId", clanId));
    }

    @Transactional
    public void editSettings(UUID me, long clanId, String description, String tag) {
        ClanMember mine = membership(me, clanId);
        if (!mine.getRank().canEditSettings()) {
            throw NexusException.forbidden("Your rank can't change clan settings");
        }
        Clan clan = clans.findById(clanId).orElseThrow();
        if (description != null) {
            clan.setDescription(filter.clean(description.strip()));
        }
        if (tag != null && !tag.equalsIgnoreCase(clan.getTag())) {
            if (mine.getRank() != ClanRank.OWNER && mine.getRank() != ClanRank.CO_OWNER) {
                throw NexusException.forbidden("Only the owner or co-owner can change the tag");
            }
            if (!tag.matches(TAG)) {
                throw NexusException.badRequest("Clan tags are 2-5 letters or numbers");
            }
            if (clans.tagTaken(tag)) {
                throw NexusException.badRequest("That clan tag is taken");
            }
            clan.setTag(tag.toUpperCase());
        }
        clans.save(clan);
        broadcast(clanId, Event.of("clan.update").with("clanId", clanId));
    }

    @Transactional
    public void delete(UUID me, long clanId) {
        ClanMember mine = membership(me, clanId);
        if (mine.getRank() != ClanRank.OWNER) {
            throw NexusException.forbidden("Only the owner can delete the clan");
        }
        List<ClanMember> everyone = members.findByClanIdOrderByJoinedAtAsc(clanId);
        broadcast(clanId, Event.of("clan.deleted").with("clanId", clanId));
        members.deleteByClanId(clanId);
        for (ClanMember m : everyone) {
            fixPrimary(m.getPlayer(), clanId);
        }
        clans.deleteById(clanId);
    }

    /** Chooses which of your clans gets your honor. */
    @Transactional
    public void setPrimary(UUID me, long clanId) {
        membership(me, clanId);
        Player player = players.findById(me).orElseThrow();
        player.setPrimaryClanId(clanId);
        players.save(player);
    }

    // ------------------------------------------------------------------ clan chat and polls

    @Transactional
    public MessageView chat(Player me, long clanId, String text) {
        membership(me.getUuid(), clanId);
        if (me.isMuted()) {
            throw NexusException.forbidden("You're muted until " + me.getMutedUntil());
        }
        ClanMessage saved = messages.save(new ClanMessage(clanId, me.getUuid(), filter.clean(text.strip())));
        MessageView view = new MessageView(saved.getId(), me.getUuid(), me.getName(), saved.getText(),
                saved.getSentAt().toString());
        broadcast(clanId, Event.of("clan.chat").with("clanId", clanId).withObject("message", view));
        return view;
    }

    @Transactional(readOnly = true)
    public List<MessageView> history(UUID me, long clanId) {
        membership(me, clanId);
        List<ClanMessage> list = new ArrayList<>(messages.findTop100ByClanIdOrderByIdDesc(clanId));
        java.util.Collections.reverse(list);
        Map<UUID, Player> names = players.findAllById(list.stream().map(ClanMessage::getFrom).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Player::getUuid, Function.identity()));
        return list.stream().map(m -> new MessageView(m.getId(), m.getFrom(),
                names.containsKey(m.getFrom()) ? names.get(m.getFrom()).getName() : "?", m.getText(),
                m.getSentAt().toString())).toList();
    }

    @Transactional
    public void createPoll(UUID me, long clanId, String question, List<String> options) {
        ClanMember mine = membership(me, clanId);
        if (!mine.getRank().canStartPolls()) {
            throw NexusException.forbidden("VIPs and higher can start polls");
        }
        List<String> clean = options.stream().map(String::strip).filter(o -> !o.isEmpty()).map(filter::clean).toList();
        if (clean.size() < 2 || clean.size() > 4 || clean.stream().anyMatch(o -> o.length() > 90 || o.contains("\n"))) {
            throw NexusException.badRequest("Polls need 2-4 short options");
        }
        polls.save(new ClanPoll(clanId, filter.clean(question.strip()), clean, me, Instant.now().plus(Duration.ofHours(24))));
        broadcast(clanId, Event.of("clan.poll").with("clanId", clanId));
    }

    @Transactional
    public void vote(UUID me, long pollId, int choice) {
        ClanPoll poll = polls.findById(pollId).orElseThrow(() -> NexusException.notFound("No such poll"));
        membership(me, poll.getClanId());
        if (!poll.isOpen()) {
            throw NexusException.badRequest("This poll has closed");
        }
        if (choice < 0 || choice >= poll.getOptions().size()) {
            throw NexusException.badRequest("Pick one of the options");
        }
        votes.save(new ClanVote(pollId, me, choice));
    }

    @Transactional(readOnly = true)
    public List<PollView> polls(UUID me, long clanId) {
        membership(me, clanId);
        List<PollView> list = new ArrayList<>();
        for (ClanPoll poll : polls.findTop20ByClanIdOrderByIdDesc(clanId)) {
            List<ClanVote> all = votes.findByPollId(poll.getId());
            List<Integer> counts = new ArrayList<>();
            for (int i = 0; i < poll.getOptions().size(); i++) {
                int option = i;
                counts.add((int) all.stream().filter(v -> v.getChoice() == option).count());
            }
            Integer mine = all.stream().filter(v -> v.getPlayer().equals(me)).map(ClanVote::getChoice).findFirst().orElse(null);
            list.add(new PollView(poll.getId(), poll.getQuestion(), poll.getOptions(), counts, mine, poll.isOpen(),
                    poll.getEndsAt().toString()));
        }
        return list;
    }

    // ------------------------------------------------------------------ admin

    /** The Nexus owner's clan: shown above all clans with a "Nexus MVP" label (not on the podium). */
    @Transactional
    public void setMvp(long clanId, boolean mvp) {
        Clan clan = clans.findById(clanId).orElseThrow(() -> NexusException.notFound("No such clan"));
        clan.setMvp(mvp);
        clans.save(clan);
    }

    /** Developer tool: add (or with a minus amount, remove) honor. Returns the clan's new total. */
    @Transactional
    public long giveHonor(long clanId, long amount) {
        Clan clan = clans.findById(clanId).orElseThrow(() -> NexusException.notFound("No such clan"));
        clan.addHonor(amount); // never goes below 0
        clans.save(clan);
        return clan.getHonor();
    }

    // ------------------------------------------------------------------ helpers

    ClanMember membership(UUID player, long clanId) {
        return members.findById(new ClanMember.Key(clanId, player))
                .orElseThrow(() -> NexusException.forbidden("You're not in that clan"));
    }

    private void removeMember(long clanId, UUID player) {
        members.deleteById(new ClanMember.Key(clanId, player));
        fixPrimary(player, clanId);
        broadcast(clanId, Event.of("clan.update").with("clanId", clanId));
    }

    private void makePrimaryIfNone(UUID player, long clanId) {
        Player p = players.findById(player).orElseThrow();
        if (p.getPrimaryClanId() == null) {
            p.setPrimaryClanId(clanId);
            players.save(p);
        }
    }

    /** They left clan `gone`: point their honor at their other clan, if any. */
    private void fixPrimary(UUID player, long gone) {
        players.findById(player).ifPresent(p -> {
            if (p.getPrimaryClanId() != null && p.getPrimaryClanId() == gone) {
                p.setPrimaryClanId(members.findByPlayer(player).stream().map(ClanMember::getClanId)
                        .filter(id -> id != gone).findFirst().orElse(null));
                players.save(p);
            }
        });
    }

    private void broadcast(long clanId, Event event) {
        for (ClanMember m : members.findByClanIdOrderByJoinedAtAsc(clanId)) {
            live.send(m.getPlayer(), event);
        }
    }

    private ClanView view(Clan clan, ClanRank myRank, boolean primary) {
        List<ClanMember> list = members.findByClanIdOrderByJoinedAtAsc(clan.getId());
        Map<UUID, Player> known = players.findAllById(list.stream().map(ClanMember::getPlayer).toList()).stream()
                .collect(Collectors.toMap(Player::getUuid, Function.identity()));
        List<MemberView> memberViews = list.stream()
                .sorted((a, b) -> a.getRank() != b.getRank() ? a.getRank().compareTo(b.getRank())
                        : Long.compare(b.getHonor(), a.getHonor()))
                .map(m -> new MemberView(m.getPlayer(), known.containsKey(m.getPlayer()) ? known.get(m.getPlayer()).getName() : "?",
                        m.getRank(), m.getHonor(), live.isOnline(m.getPlayer())))
                .toList();
        List<String> invited = new ArrayList<>();
        if (myRank.canInviteAndKick()) {
            List<UUID> ids = invites.findByClanId(clan.getId()).stream().map(ClanInvite::getPlayer).toList();
            players.findAllById(ids).forEach(p -> invited.add(p.getName()));
        }
        int[] place = placeOf(clan);
        return new ClanView(clan.getId(), clan.getName(), clan.getTag(), clan.getDescription(), clan.getHonor(),
                clan.isMvp(), place[0], place[1] == 1, myRank, primary, memberViews, invited);
    }

    /** {place on the leaderboard (0 = not in the top 100), 1 if on the podium}. */
    private int[] placeOf(Clan clan) {
        List<Clan> board = clans.leaderboard();
        List<Long> podium = clans.podium().stream().map(Clan::getId).toList();
        int place = 0;
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).getId().equals(clan.getId())) {
                place = i + 1;
            }
        }
        return new int[] {place, podium.contains(clan.getId()) ? 1 : 0};
    }

    private List<ClanSummary> summaries(List<Clan> list) {
        List<Long> podium = clans.podium().stream().map(Clan::getId).toList();
        List<Clan> board = clans.leaderboard();
        Map<Long, Long> counts = members.findByClanIdIn(list.stream().map(Clan::getId).toList()).stream()
                .collect(Collectors.groupingBy(ClanMember::getClanId, Collectors.counting()));
        List<ClanSummary> result = new ArrayList<>();
        for (Clan c : list) {
            int place = board.indexOf(c) + 1;
            result.add(new ClanSummary(c.getId(), c.getName(), c.getTag(), c.getHonor(), c.isMvp(), place,
                    podium.contains(c.getId()), counts.getOrDefault(c.getId(), 0L)));
        }
        return result;
    }
}
