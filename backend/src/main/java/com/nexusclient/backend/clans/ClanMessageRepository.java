package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClanMessageRepository extends JpaRepository<ClanMessage, Long> {

    /** The newest 100 messages (the launcher shows them oldest first). */
    List<ClanMessage> findTop100ByClanIdOrderByIdDesc(Long clanId);
}
