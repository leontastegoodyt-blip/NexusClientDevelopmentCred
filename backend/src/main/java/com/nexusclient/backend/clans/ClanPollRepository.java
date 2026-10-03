package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClanPollRepository extends JpaRepository<ClanPoll, Long> {

    List<ClanPoll> findTop20ByClanIdOrderByIdDesc(Long clanId);
}
