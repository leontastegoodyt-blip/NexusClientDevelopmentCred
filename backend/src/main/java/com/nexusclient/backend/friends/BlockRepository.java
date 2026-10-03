package com.nexusclient.backend.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Reads and saves blocks. */
public interface BlockRepository extends JpaRepository<Block, Block.Key> {

    List<Block> findByBlocker(UUID blocker);

    /** True if either player has blocked the other. */
    @Query("select count(b) > 0 from Block b where (b.blocker = :a and b.blocked = :b) or (b.blocker = :b and b.blocked = :a)")
    boolean eitherBlocked(UUID a, UUID b);
}
