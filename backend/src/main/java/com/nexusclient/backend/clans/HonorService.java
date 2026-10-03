package com.nexusclient.backend.clans;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.Player;
import com.nexusclient.backend.account.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * HonorService – clan honor (the project brief section 6, Honor and leaderboard):
 *  - 100 honor per hour of real play (given as 5 honor every 3 active minutes)
 *  - daily limit: 4 h 30 min of play counts per player per day (= 450 honor, days in UTC)
 *  - honor events multiply the honor from those minutes (the highest running event wins)
 *  - AFK minutes LOSE honor: 50 per hour (5 every 6 AFK minutes); honor never goes below 0
 * The game reports once a minute (POST /api/activity); reports closer than 50 seconds are ignored,
 * so a modified client can't send extra minutes.
 */
@Service
public class HonorService {

    public static final int HONOR_PER_HOUR = 100;
    public static final int DAILY_MINUTES = 270;       // 4 h 30 min → 450 honor
    private static final int AFK_HONOR_PER_HOUR = 50;
    private static final long MIN_SECONDS_BETWEEN_REPORTS = 50;

    /** What the game gets back (shown in the mod: "Honor today: 120 / 450"). */
    public record Result(boolean counted, boolean active, long honorToday, int countedMinutes, int dailyLimitMinutes,
                         double multiplier, String clanTag) {}

    private final ActivityDayRepository days;
    private final HonorEventRepository events;
    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final PlayerRepository players;
    private final AfkDetector detector;

    public HonorService(ActivityDayRepository days, HonorEventRepository events, ClanRepository clans,
                        ClanMemberRepository members, PlayerRepository players, AfkDetector detector) {
        this.days = days;
        this.events = events;
        this.clans = clans;
        this.members = members;
        this.players = players;
        this.detector = detector;
    }

    @Transactional
    public Result report(UUID me, AfkDetector.Minute minute) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ActivityDay day = days.findById(new ActivityDay.Key(me, today)).orElseGet(() -> new ActivityDay(me, today));
        double multiplier = multiplierAt(now);
        Player player = players.findById(me).orElseThrow();
        Clan clan = player.getPrimaryClanId() == null ? null : clans.findById(player.getPrimaryClanId()).orElse(null);
        String tag = clan == null ? null : clan.getTag();

        if (day.getLastReportAt() != null && day.getLastReportAt().plusSeconds(MIN_SECONDS_BETWEEN_REPORTS).isAfter(now)) {
            return new Result(false, false, day.getHonor(), day.getCountedMinutes(), DAILY_MINUTES, multiplier, tag);
        }
        day.reported(now);

        boolean active = detector.isActive(me, minute);
        long honor = 0;
        if (active) {
            boolean counted = day.getCountedMinutes() < DAILY_MINUTES;
            day.addActive(counted);
            if (counted && day.getCountedMinutes() % 3 == 0) {
                honor = Math.round(HONOR_PER_HOUR / 20.0 * multiplier); // 5 honor per 3 minutes
            }
        } else {
            day.addAfk();
            if (day.getAfkMinutes() % 6 == 0) {
                honor = -AFK_HONOR_PER_HOUR / 10; // -5 honor per 6 AFK minutes
            }
        }
        if (honor != 0 && clan != null) {
            day.addHonor(honor);
            clan.addHonor(honor);
            clans.save(clan);
            final long change = honor;
            members.findById(new ClanMember.Key(clan.getId(), me)).ifPresent(member -> {
                member.addHonor(change);
                members.save(member);
            });
        }
        days.save(day);
        return new Result(true, active, day.getHonor(), day.getCountedMinutes(), DAILY_MINUTES, multiplier, tag);
    }

    /** The highest multiplier of the events running now (1 if none). */
    public double multiplierAt(Instant when) {
        return events.runningAt(when).stream().mapToDouble(HonorEvent::getMultiplier).max().orElse(1.0);
    }

    public List<HonorEvent> upcomingEvents() {
        return events.notFinished(Instant.now());
    }

    @Transactional
    public HonorEvent createEvent(String name, double multiplier, Instant startsAt, Instant endsAt) {
        if (multiplier < 1 || multiplier > 10) {
            throw NexusException.badRequest("The multiplier must be between 1 and 10");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw NexusException.badRequest("The event must end after it starts");
        }
        return events.save(new HonorEvent(name, multiplier, startsAt, endsAt));
    }

    @Transactional
    public void deleteEvent(long id) {
        events.deleteById(id);
    }
}
