package com.nexusclient.backend.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** PlayerRepository – reads and saves players. Spring writes the database code from the method names. */
public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findFirstByNameIgnoreCaseOrderByLastSeenDesc(String name);
}
