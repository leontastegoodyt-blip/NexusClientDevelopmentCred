package com.nexusclient.backend.messages;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /** The conversation between two players, newest first, older than `before` (a message id). */
    @Query("select m from DirectMessage m where ((m.from = :a and m.to = :b) or (m.from = :b and m.to = :a))"
            + " and m.id < :before order by m.id desc")
    List<DirectMessage> conversation(UUID a, UUID b, long before, Pageable page);

    /** Unread messages to a player, grouped by sender: [sender uuid, count]. */
    @Query("select m.from, count(m) from DirectMessage m where m.to = :me and m.readAt is null group by m.from")
    List<Object[]> unreadCounts(UUID me);

    @Modifying
    @Query("update DirectMessage m set m.readAt = :now where m.to = :me and m.from = :from and m.readAt is null")
    int markRead(UUID me, UUID from, Instant now);

    long countByFromAndSentAtAfter(UUID from, Instant after);
}
