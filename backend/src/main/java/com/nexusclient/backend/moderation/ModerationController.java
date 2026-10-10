package com.nexusclient.backend.moderation;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import com.nexusclient.backend.account.SessionRepository;
import com.nexusclient.backend.account.SignedInCheck;
import com.nexusclient.backend.live.Event;
import com.nexusclient.backend.live.LiveHub;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexusclient.backend.clans.ClanService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ModerationController – reports and staff tools.
 *   POST /api/reports {target, reason, details, evidence}      any player (max 20 a day)
 *   GET  /api/admin/reports                                    staff: open reports
 *   POST /api/admin/reports/{id}/close                         staff
 *   POST /api/admin/players/{uuid}/mute {hours}                staff (0 = unmute)
  *   POST /api/admin/players/{uuid}/ban  {hours, reason}        admins only (0 = unban). Signs them out of Nexus.
 *   POST /api/admin/players/{uuid}/kick {reason}               admins only: signs them out of Nexus right now
 *                                                              (they can sign in again – a warning, not a ban)
 *   GET  /api/admin/players?search=name                        staff: find players (Developer tab)
 *   GET  /api/admin/overview                                   staff: numbers for the Developer tab
 * Nobody can ban or kick the developer, and only the developer can ban or kick admins.
 */
@RestController
public class ModerationController {

    public record ReportRequest(@NotNull UUID target, @NotNull Report.Reason reason,
                                @Size(max = 1000) String details, @Size(max = 20000) String evidence) {}

    public record MuteRequest(@Min(0) @Max(24 * 365) int hours) {}

    public record KickRequest(@Size(max = 255) String reason) {}

    /** One player in the Developer tab's search results. */
    public record PlayerInfo(UUID uuid, String name, Player.Role role, boolean online, String server, String lastSeen,
                             String bannedUntil, String banReason, String mutedUntil, boolean premium,
                             long playtimeMinutes) {}

    public record Overview(long players, int onlineNow, long openReports, long banned) {}

    public record BanRequest(@Min(0) @Max(24 * 365 * 10) int hours, @Size(max = 255) String reason) {}

    private final ReportRepository reports;
    private final PlayerRepository players;
    private final SessionRepository sessions;
    private final LiveHub live;
    private final ClanService clans;

    public ModerationController(ReportRepository reports, PlayerRepository players, SessionRepository sessions,
                                LiveHub live, ClanService clans) {
        this.clans = clans;
        this.reports = reports;
        this.players = players;
        this.sessions = sessions;
        this.live = live;
    }

    @PostMapping("/api/reports")
    public Map<String, Boolean> report(@RequestAttribute(SignedInCheck.ME) UUID me, @Valid @RequestBody ReportRequest body) {
        if (body.target().equals(me) || !players.existsById(body.target())) {
            throw NexusException.badRequest("That player can't be reported");
        }
        if (reports.countByReporterAndCreatedAtAfter(me, Instant.now().minus(Duration.ofDays(1))) >= 20) {
            throw NexusException.badRequest("You've sent a lot of reports today – thanks! Try again tomorrow");
        }
        reports.save(new Report(me, body.target(), body.reason(), body.details(), body.evidence()));
        return Map.of("ok", true);
    }

    @GetMapping("/api/admin/reports")
    public List<ReportView> openReports(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me) {
        requireStaff(me);
        return reports.findTop100ByStatusOrderByCreatedAtAsc("OPEN").stream()
                .map(r -> new ReportView(r.getId(), r.getReporter(), nameOf(r.getReporter()), r.getTarget(),
                        nameOf(r.getTarget()), r.getReason(), r.getDetails(), String.valueOf(r.getCreatedAt())))
                .toList();
    }

    /** One open report, with both players' names (for the launcher's Developer tab). */
    public record ReportView(long id, UUID reporter, String reporterName, UUID target, String targetName,
                             String reason, String details, String createdAt) {}

    private String nameOf(UUID uuid) {
        return players.findById(uuid).map(Player::getName).orElse("Unknown");
    }

    @PostMapping("/api/admin/reports/{id}/close")
    @Transactional
    public Map<String, Boolean> close(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable long id) {
        requireStaff(me);
        Report report = reports.findById(id).orElseThrow(() -> NexusException.notFound("No such report"));
        report.close(me.getUuid());
        return Map.of("ok", true);
    }

    @PostMapping("/api/admin/players/{uuid}/mute")
    @Transactional
    public Map<String, Boolean> mute(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable UUID uuid,
                                     @Valid @RequestBody MuteRequest body) {
        requireStaff(me);
        Player target = players.findById(uuid).orElseThrow(() -> NexusException.notFound("No such player"));
        target.mute(body.hours() == 0 ? null : Instant.now().plus(Duration.ofHours(body.hours())));
        players.save(target);
        if (body.hours() > 0) {
            live.send(uuid, Event.of("notice").with("text", "You've been muted in Nexus chat for " + body.hours() + " hours."));
        }
        return Map.of("ok", true);
    }

    @PostMapping("/api/admin/players/{uuid}/ban")
    @Transactional
    public Map<String, Boolean> ban(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable UUID uuid,
                                    @Valid @RequestBody BanRequest body) {
        Player target = removableTarget(me, uuid, "ban");
        Instant until = body.hours() == 0 ? null : Instant.now().plus(Duration.ofHours(body.hours()));
        target.ban(until, body.reason());
        players.save(target);
        if (until != null) {
            // Tell their launcher why before cutting it off, then sign them out everywhere.
            live.send(uuid, Event.of("banned").with("until", until.toString())
                    .with("reason", body.reason() == null ? "" : body.reason()));
            sessions.deleteAllFor(uuid);
            live.disconnect(uuid);
        }
        return Map.of("ok", true);
    }

    @PostMapping("/api/admin/players/{uuid}/kick")
    @Transactional
    public Map<String, Boolean> kick(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me, @PathVariable UUID uuid,
                                     @Valid @RequestBody KickRequest body) {
        removableTarget(me, uuid, "kick");
        live.send(uuid, Event.of("kicked").with("reason", body.reason() == null ? "" : body.reason()));
        sessions.deleteAllFor(uuid);
        live.disconnect(uuid);
        return Map.of("ok", true);
    }

    @GetMapping("/api/admin/players")
    public List<PlayerInfo> search(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me,
                                   @RequestParam(defaultValue = "") String search) {
        requireStaff(me);
        return players.findTop25ByNameContainingIgnoreCaseOrderByLastSeenDesc(search.trim()).stream()
                .map(p -> new PlayerInfo(p.getUuid(), p.getName(), p.getRole(), live.isOnline(p.getUuid()),
                        live.serverOf(p.getUuid()), String.valueOf(p.getLastSeen()),
                        p.isBanned() ? p.getBannedUntil().toString() : null, p.isBanned() ? p.getBanReason() : null,
                        p.isMuted() ? p.getMutedUntil().toString() : null, clans.isPremium(p.getUuid()),
                        p.getPlaytimeMinutes()))
                .toList();
    }

    @GetMapping("/api/admin/overview")
    public Overview overview(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me) {
        requireStaff(me);
        return new Overview(players.count(), live.onlineCount(), reports.countByStatus("OPEN"),
                players.countByBannedUntilAfter(Instant.now()));
    }

    /**
     * The player an admin wants to ban or kick – after checking they're allowed: admins only, never
     * yourself, never the developer, and only the developer can remove other admins.
     */
    private Player removableTarget(Player me, UUID uuid, String action) {
        if (!me.isAdmin()) {
            throw NexusException.forbidden("Only admins can " + action + " players");
        }
        Player target = players.findById(uuid).orElseThrow(() -> NexusException.notFound("No such player"));
        if (target.getUuid().equals(me.getUuid())) {
            throw NexusException.badRequest("You can't " + action + " yourself");
        }
        if (target.isDeveloper() || (target.isAdmin() && !me.isDeveloper())) {
            throw NexusException.forbidden("You can't " + action + " " + target.getName());
        }
        return target;
    }

    private static void requireStaff(Player me) {
        if (!me.isStaff()) {
            throw NexusException.forbidden("Staff only");
        }
    }
}
