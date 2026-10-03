package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClanInviteRepository extends JpaRepository<ClanInvite, ClanInvite.Key> {

    List<ClanInvite> findByPlayer(UUID player);

    List<ClanInvite> findByClanId(Long clanId);
}
