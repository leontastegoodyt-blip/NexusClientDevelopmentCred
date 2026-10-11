package com.nexusclient.backend.clans;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ClanController – the clan web addresses:
 *   GET  /api/clans/mine                       your clans (members, ranks, honor, place)
 *   GET  /api/clans/invites                    clans that invited you
 *   GET  /api/clans/leaderboard                top 100 (Nexus MVP first, podium marked)
 *   GET  /api/clans/search?q=                  find clans by name
 *   GET  /api/clans/tags?uuids=a,b             clan tags for names in-game
 *   POST /api/clans {name, tag}                create
 *   POST /api/clans/{id}/invite {name}         POST /api/clans/{id}/accept   /decline   /leave
 *   POST /api/clans/{id}/kick {uuid}           POST /api/clans/{id}/rank {uuid, rank}
 *   POST /api/clans/{id}/transfer {uuid}       POST /api/clans/{id}/settings {description, tag}
 *   POST /api/clans/{id}/primary               DELETE /api/clans/{id}
 *   GET/POST /api/clans/{id}/chat {text}       GET/POST /api/clans/{id}/polls {question, options}
 *   POST /api/clans/polls/{pollId}/vote {choice}
 *   POST /api/activity {moveBlocks, turnDegrees, keyPresses, clicks, chatMessages, menuSeconds}
 *   GET  /api/events                           running and upcoming honor events
 *   Admin: POST /api/admin/events {name, multiplier, startsAt, endsAt}, DELETE /api/admin/events/{id},
 *          POST /api/admin/clans/{id}/mvp {mvp}, developer only: POST /api/admin/clans/{id}/honor {amount}
 */
@RestController
public class ClanController {

    public record CreateRequest(@NotBlank String name, @NotBlank String tag) {}

    public record NameRequest(@NotBlank String name) {}

    public record PlayerRequest(@NotNull UUID uuid) {}

    public record RankRequest(@NotNull UUID uuid, @NotNull ClanRank rank) {}

    public record SettingsRequest(@Size(max = 300) String description, String tag) {}

    public record ChatRequest(@NotBlank @Size(max = 256) String text) {}

    public record PollRequest(@NotBlank @Size(max = 200) String question, @NotNull List<String> options) {}

    public record VoteRequest(int choice) {}

    public record EventRequest(@NotBlank @Size(max = 64) String name, double multiplier, @NotNull Instant startsAt,
                               @NotNull Instant endsAt) {}

    public record MvpRequest(boolean mvp) {}

    private static final Map<String, Boolean> OK = Map.of("ok", true);
    private final ClanService clans;
    private final HonorService honor;

    public ClanController(ClanService clans, HonorService honor) {
        this.clans = clans;
        this.honor = honor;
    }

    @GetMapping("/api/clans/mine")
    public List<ClanService.ClanView> mine(@RequestAttribute(SignedInCheck.ME) UUID me) {
        return clans.myClans(me);
    }

    @GetMapping("/api/clans/invites")
    public List<ClanService.InviteView> invites(@RequestAttribute(SignedInCheck.ME) UUID me) {
        return clans.myInvites(me);
    }

    @GetMapping("/api/clans/leaderboard")
    public List<ClanService.ClanSummary> leaderboard() {
        return clans.leaderboard();
    }

    @GetMapping("/api/clans/search")
    public List<ClanService.ClanSummary> search(@RequestParam("q") String text) {
        return clans.search(text);
    }

    @GetMapping("/api/clans/tags")
    public Map<String, String> tags(@RequestParam String uuids) {
        List<UUID> list = Arrays.stream(uuids.split(",")).limit(200).map(String::trim).filter(s -> !s.isEmpty())
                .map(UUID::fromString).toList();
        return clans.tags(list);
    }

    @PostMapping("/api/clans")
    public ClanService.ClanView create(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody CreateRequest body) {
        return clans.create(me, body.name(), body.tag());
    }

    @PostMapping("/api/clans/{id}/invite")
    public Map<String, Boolean> invite(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id,
                                       @Valid @RequestBody NameRequest body) {
        clans.invite(me, id, body.name());
        return OK;
    }

    @PostMapping("/api/clans/{id}/accept")
    public Map<String, Boolean> accept(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        clans.acceptInvite(me, id);
        return OK;
    }

    @PostMapping("/api/clans/{id}/decline")
    public Map<String, Boolean> decline(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        clans.declineInvite(me, id);
        return OK;
    }

    @PostMapping("/api/clans/{id}/leave")
    public Map<String, Boolean> leave(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        clans.leave(me, id);
        return OK;
    }

    @PostMapping("/api/clans/{id}/kick")
    public Map<String, Boolean> kick(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id,
                                     @Valid @RequestBody PlayerRequest body) {
        clans.kick(me, id, body.uuid());
        return OK;
    }

    @PostMapping("/api/clans/{id}/rank")
    public Map<String, Boolean> rank(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id,
                                     @Valid @RequestBody RankRequest body) {
        clans.setRank(me, id, body.uuid(), body.rank());
        return OK;
    }

    @PostMapping("/api/clans/{id}/transfer")
    public Map<String, Boolean> transfer(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id,
                                         @Valid @RequestBody PlayerRequest body) {
        clans.transfer(me, id, body.uuid());
        return OK;
    }

    @PostMapping("/api/clans/{id}/settings")
    public Map<String, Boolean> settings(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id,
                                         @Valid @RequestBody SettingsRequest body) {
        clans.editSettings(me, id, body.description(), body.tag());
        return OK;
    }

    @PostMapping("/api/clans/{id}/primary")
    public Map<String, Boolean> primary(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        clans.setPrimary(me, id);
        return OK;
    }

    @DeleteMapping("/api/clans/{id}")
    public Map<String, Boolean> delete(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        clans.delete(me, id);
        return OK;
    }

    @GetMapping("/api/clans/{id}/chat")
    public List<ClanService.MessageView> history(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        return clans.history(me, id);
    }

    @PostMapping("/api/clans/{id}/chat")
    public ClanService.MessageView chat(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id,
                                        @Valid @RequestBody ChatRequest body) {
        return clans.chat(me, id, body.text());
    }

    @GetMapping("/api/clans/{id}/polls")
    public List<ClanService.PollView> polls(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id) {
        return clans.polls(me, id);
    }

    @PostMapping("/api/clans/{id}/polls")
    public Map<String, Boolean> createPoll(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long id,
                                           @Valid @RequestBody PollRequest body) {
        clans.createPoll(me, id, body.question(), body.options());
        return OK;
    }

    @PostMapping("/api/clans/polls/{pollId}/vote")
    public Map<String, Boolean> vote(@RequestAttribute(SignedInCheck.ME) UUID me, @PathVariable long pollId,
                                     @RequestBody VoteRequest body) {
        clans.vote(me, pollId, body.choice());
        return OK;
    }

    // ------------------------------------------------------------------ honor

    @PostMapping("/api/activity")
    public HonorService.Result activity(@RequestAttribute(SignedInCheck.ME) UUID me, @RequestBody AfkDetector.Minute minute) {
        com.nexusclient.backend.badges.BadgeController.markInGame(me); // they're in-game with Nexus (badges)
        return honor.report(me, minute);
    }

    @GetMapping("/api/events")
    public List<HonorEvent> events() {
        return honor.upcomingEvents();
    }

    // ------------------------------------------------------------------ admin (the Nexus owner)

    @PostMapping("/api/admin/events")
    public HonorEvent createEvent(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @Valid @RequestBody EventRequest body) {
        requireAdmin(me);
        return honor.createEvent(body.name(), body.multiplier(), body.startsAt(), body.endsAt());
    }

    @DeleteMapping("/api/admin/events/{id}")
    public Map<String, Boolean> deleteEvent(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id) {
        requireAdmin(me);
        honor.deleteEvent(id);
        return OK;
    }

    @PostMapping("/api/admin/clans/{id}/mvp")
    public Map<String, Boolean> mvp(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id,
                                    @RequestBody MvpRequest body) {
        requireAdmin(me);
        clans.setMvp(id, body.mvp());
        return OK;
    }

    public record HonorRequest(long amount) {}

    /** Developer only: give a clan honor (a minus amount takes it away). {"honor": new total} */
    @PostMapping("/api/admin/clans/{id}/honor")
    public Map<String, Long> giveHonor(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id,
                                       @RequestBody HonorRequest body) {
        if (!me.isDeveloper()) {
            throw NexusException.forbidden("Only the Nexus developer can change honor");
        }
        if (Math.abs(body.amount()) > 10_000_000) {
            throw NexusException.badRequest("That's too much honor at once");
        }
        return Map.of("honor", clans.giveHonor(id, body.amount()));
    }

    private static void requireAdmin(Player me) {
        if (!me.isAdmin()) {
            throw NexusException.forbidden("Admins only");
        }
    }
}
