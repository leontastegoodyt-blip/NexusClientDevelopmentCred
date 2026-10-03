package com.nexusclient.backend.partners;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** PartnerServer – a Nexus Partner Server: featured in the launcher, with its own Nexus mod rules. */
@Entity
@Table(name = "partner_servers")
public class PartnerServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String address;
    private String description;
    @Column(name = "blocked_mods")
    private String blockedMods = "";
    @Column(name = "manager_uuid")
    private UUID manager;
    @Column(name = "sort_order")
    private int sortOrder;
    @Column(name = "created_at")
    private Instant createdAt;

    protected PartnerServer() {}

    public PartnerServer(String name, String address, String description, UUID manager) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.manager = manager;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public UUID getManager() { return manager; }
    public int getSortOrder() { return sortOrder; }

    public List<String> getBlockedMods() {
        return blockedMods == null || blockedMods.isBlank() ? List.of()
                : Arrays.stream(blockedMods.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setBlockedMods(List<String> mods) {
        this.blockedMods = String.join(",", mods);
    }
}
