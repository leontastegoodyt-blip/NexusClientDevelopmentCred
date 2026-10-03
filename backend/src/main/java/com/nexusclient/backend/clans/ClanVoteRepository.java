package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClanVoteRepository extends JpaRepository<ClanVote, ClanVote.Key> {

    List<ClanVote> findByPollId(Long pollId);
}
