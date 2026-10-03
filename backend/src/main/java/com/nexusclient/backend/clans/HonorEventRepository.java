package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface HonorEventRepository extends JpaRepository<HonorEvent, Long> {

    /** Events running at this moment. */
    @Query("select e from HonorEvent e where e.startsAt <= :now and e.endsAt > :now")
    List<HonorEvent> runningAt(Instant now);

    /** Running and upcoming events, soonest first. */
    @Query("select e from HonorEvent e where e.endsAt > :now order by e.startsAt asc")
    List<HonorEvent> notFinished(Instant now);
}
