package com.nexusclient.backend.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PlayerRepository – reads and saves players. Spring writes the database code from the method names. */
public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findFirstByNameIgnoreCaseOrderByLastSeenDesc(String name);

    /** Developer tab search: players whose name contains the text, most recently seen first. */
    List<Player> findTop25ByNameContainingIgnoreCaseOrderByLastSeenDesc(String text);

    /** Developer tab: how many players are banned right now. */
    long countByBannedUntilAfter(Instant now);
}
