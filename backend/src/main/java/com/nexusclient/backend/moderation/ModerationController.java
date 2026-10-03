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
import org.springframework.web.bind.annotation.RestController;

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
 *   POST /api/admin/players/{uuid}/ban  {hours, reason}        admins only (0 = unban)
 */
@RestController
public class ModerationController {

    public record ReportRequest(@NotNull UUID target, @NotNull Report.Reason reason,
                                @Size(max = 1000) String details, @Size(max = 20000) String evidence) {}

    public record MuteRequest(@Min(0) @Max(24 * 365) int hours) {}

    public record BanRequest(@Min(0) @Max(24 * 365 * 10) int hours, @Size(max = 255) String reason) {}

    private final ReportRepository reports;
    private final PlayerRepository players;
    private final SessionRepository sessions;
    private final LiveHub live;

    public ModerationController(ReportRepository reports, PlayerRepository players, SessionRepository sessions,
                                LiveHub live) {
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
    public List<Report> openReports(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me) {
        requireStaff(me);
        return reports.findTop100ByStatusOrderByCreatedAtAsc("OPEN");
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
        if (me.getRole() != Player.Role.ADMIN) {
            throw NexusException.forbidden("Only admins can ban");
        }
        Player target = players.findById(uuid).orElseThrow(() -> NexusException.notFound("No such player"));
        target.ban(body.hours() == 0 ? null : Instant.now().plus(Duration.ofHours(body.hours())), body.reason());
        players.save(target);
        if (body.hours() > 0) {
            sessions.deleteAllFor(uuid); // signs them out everywhere
            live.disconnect(uuid);
        }
        return Map.of("ok", true);
    }

    private static void requireStaff(Player me) {
        if (!me.isStaff()) {
            throw NexusException.forbidden("Staff only");
        }
    }
}
