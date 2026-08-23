package com.alexastudillo.partyregistry.infrastructure.persistence.party.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

public class PartyNationalityEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;
}
