package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Maps the details stored for a natural-person party.
 */
@Entity
@Table(name = "natural_person_details")
public class NaturalPersonDetailsEntity {

    @Id
    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private PartyEntity party;

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

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "birth_country_code", columnDefinition = "char(2)")
    private String birthCountryCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected NaturalPersonDetailsEntity() {
    }

    NaturalPersonDetailsEntity(
            UUID partyId,
            NaturalPersonDetails details,
            AuditInfo auditInfo) {
        this.partyId = partyId;
        this.givenNames = details.givenNames();
        this.familyNames = details.familyNames();
        this.preferredName = details.preferredName();
        this.birthDate = details.birthDate();
        this.dateOfDeath = details.dateOfDeath();
        this.birthCountryCode = details.birthCountryCode();
        this.createdAt = auditInfo.createdAt();
        this.createdBy = auditInfo.createdBy();
        this.updatedAt = auditInfo.updatedAt();
        this.updatedBy = auditInfo.updatedBy();
    }

    void attachTo(PartyEntity root) {
        party = root;
        partyId = root.id();
    }

    String givenNames() {
        return givenNames;
    }

    String familyNames() {
        return familyNames;
    }

    String preferredName() {
        return preferredName;
    }

    LocalDate birthDate() {
        return birthDate;
    }

    LocalDate dateOfDeath() {
        return dateOfDeath;
    }

    String birthCountryCode() {
        return birthCountryCode;
    }

    Instant createdAt() {
        return createdAt;
    }

    String createdBy() {
        return createdBy;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    String updatedBy() {
        return updatedBy;
    }

}
