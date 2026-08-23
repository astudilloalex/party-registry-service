package com.alexastudillo.partyregistry.infrastructure.persistence.party.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

public class LegalEntityDetailsEntity {

    @Id
    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "legal_name", nullable = false, length = 300)
    private String legalName;

    @Column(name = "trade_name", length = 300)
    private String tradeName;

    @Column(name = "legal_form_code", length = 64)
    private String legalFormCode;

    @Column(name = "incorporation_country_code", nullable = false, length = 2)
    private String incorporationCountryCode;

    @Column(name = "incorporated_on")
    private LocalDate incorporatedOn;

    @Column(name = "dissolved_on")
    private LocalDate dissolvedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;
}
