package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClanMemberRepository extends JpaRepository<ClanMember, ClanMember.Key> {

    List<ClanMember> findByClanIdOrderByJoinedAtAsc(Long clanId);

    List<ClanMember> findByPlayer(UUID player);

    List<ClanMember> findByPlayerIn(Collection<UUID> players);

    List<ClanMember> findByClanIdIn(Collection<Long> clanIds);

    long countByClanId(Long clanId);

    long countByClanIdAndRank(Long clanId, ClanRank rank);

    long countByPlayer(UUID player);

    void deleteByClanId(Long clanId);
}
