package com.nexusclient.backend.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and saves friend requests. */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findByFromAndTo(UUID from, UUID to);

    List<FriendRequest> findByToOrderByCreatedAtDesc(UUID to);

    List<FriendRequest> findByFromOrderByCreatedAtDesc(UUID from);

    long countByFrom(UUID from);

    @Modifying
    @Query("delete from FriendRequest r where (r.from = :a and r.to = :b) or (r.from = :b and r.to = :a)")
    int deleteBetween(UUID a, UUID b);
}
