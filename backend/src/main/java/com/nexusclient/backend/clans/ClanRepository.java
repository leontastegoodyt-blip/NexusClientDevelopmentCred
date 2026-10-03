package com.nexusclient.backend.clans;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ClanRepository extends JpaRepository<Clan, Long> {

    /** True if a clan already has this name (capitals don't matter). */
    @Query("select count(c) > 0 from Clan c where lower(c.name) = lower(:name)")
    boolean nameTaken(String name);

    /** True if a clan already has this tag (capitals don't matter). */
    @Query("select count(c) > 0 from Clan c where lower(c.tag) = lower(:tag)")
    boolean tagTaken(String tag);

    long countByOwner(UUID owner);

    /** The leaderboard: the Nexus MVP clan first, then by honor. */
    @Query("select c from Clan c order by c.mvp desc, c.honor desc, c.createdAt asc limit 100")
    List<Clan> leaderboard();

    /** The top 3 clans by honor, not counting the MVP clan (the podium). */
    @Query("select c from Clan c where c.mvp = false order by c.honor desc, c.createdAt asc limit 3")
    List<Clan> podium();

    List<Clan> findTop30ByNameContainingIgnoreCaseOrderByHonorDesc(String text);
}
