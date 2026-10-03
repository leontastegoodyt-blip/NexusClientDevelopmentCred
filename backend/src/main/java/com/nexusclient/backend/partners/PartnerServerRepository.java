package com.nexusclient.backend.partners;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerServerRepository extends JpaRepository<PartnerServer, Long> {

    List<PartnerServer> findAllByOrderBySortOrderAscNameAsc();

    List<PartnerServer> findByManager(UUID manager);

    boolean existsByAddressIgnoreCase(String address);
}
