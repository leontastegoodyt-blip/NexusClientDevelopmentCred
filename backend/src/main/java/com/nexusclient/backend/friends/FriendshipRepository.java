package com.nexusclient.backend.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Reads and saves friendships (each one is stored both ways). */
public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.Key> {

    List<Friendship> findByPlayerOrderBySinceAsc(UUID player);

    boolean existsByPlayerAndFriend(UUID player, UUID friend);

    long countByPlayer(UUID player);

    @Modifying
    @Query("delete from Friendship f where (f.player = :a and f.friend = :b) or (f.player = :b and f.friend = :a)")
    int deleteBetween(UUID a, UUID b);
}
