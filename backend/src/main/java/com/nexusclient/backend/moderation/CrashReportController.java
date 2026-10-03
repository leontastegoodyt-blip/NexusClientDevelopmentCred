package com.nexusclient.backend.moderation;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.AuthService;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.SignedInCheck;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrashReportController – opt-in crash reports from the launcher (the project brief release checklist).
 *   POST /api/crash-reports {version, report}   public (works without Nexus sign-in); max 200 KB,
 *                                               10 per hour per internet address
 *   GET  /api/admin/crash-reports               staff: the newest 50
 * A report holds system details, the mod list and the end of the game log (see the launcher's
 * ErrorScanner.errorReport) – never passwords or sign-in tokens (the launcher blanks those out).
 */
@RestController
public class CrashReportController {

    public record CrashReport(@Size(max = 32) String version, @NotBlank @Size(max = 200_000) String report) {}

    public record StoredReport(long id, String player, String version, String report, String createdAt) {}

    private static final int PER_HOUR = 10;
    private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final JdbcClient db;
    private final AuthService auth;

    public CrashReportController(JdbcClient db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @PostMapping("/api/crash-reports")
    public Map<String, Boolean> receive(HttpServletRequest request, @Valid @RequestBody CrashReport body) {
        if (!allow(request.getRemoteAddr())) {
            throw new NexusException(HttpStatus.TOO_MANY_REQUESTS, "Too many crash reports – thanks, we have enough for now");
        }
        String header = request.getHeader("Authorization");
        UUID player = header != null && header.startsWith("Bearer ")
                ? auth.playerFor(header.substring(7)).map(Player::getUuid).orElse(null) : null;
        db.sql("insert into crash_reports (player_uuid, app_version, report, created_at) values (?, ?, ?, ?)")
                .param(player).param(body.version()).param(body.report()).param(Timestamp.from(Instant.now())).update();
        return Map.of("ok", true);
    }

    @GetMapping("/api/admin/crash-reports")
    public List<StoredReport> latest(@RequestAttribute(SignedInCheck.ME_PLAYER) Player me) {
        if (!me.isStaff()) {
            throw NexusException.forbidden("Staff only");
        }
        return db.sql("select id, player_uuid, app_version, report, created_at from crash_reports order by id desc limit 50")
                .query((rs, row) -> new StoredReport(rs.getLong("id"), rs.getString("player_uuid"),
                        rs.getString("app_version"), rs.getString("report"), String.valueOf(rs.getTimestamp("created_at"))))
                .list();
    }

    /** At most 10 reports per hour from one internet address (stops floods). */
    private boolean allow(String address) {
        long now = System.currentTimeMillis();
        Deque<Long> times = recent.computeIfAbsent(address, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > 3_600_000) {
                times.removeFirst();
            }
            if (times.size() >= PER_HOUR) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
