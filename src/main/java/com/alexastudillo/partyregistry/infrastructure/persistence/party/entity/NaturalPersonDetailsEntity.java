package com.alexastudillo.partyregistry.infrastructure.persistence.party.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

public class NaturalPersonDetailsEntity {

    @Id
    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "given_names", nullable = false, length = 200)
    private String givenNames;

    @Column(name = "family_names", nullable = false, length = 200)
    private String familyNames;

    @Column(name = "preferred_name", length = 200)
    private String preferredName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "date_of_death")
    private LocalDate dateOfDeath;

    @Column(name = "birth_country_code", length = 2)
    private String birthCountryCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;
}
