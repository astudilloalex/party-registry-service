package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
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
 * Maps the details stored for a legal-entity Party.
 */
@Entity
@Table(name = "legal_entity_details")
public class LegalEntityDetailsEntity {

    @Id
    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private PartyEntity party;

    @Column(name = "legal_name", nullable = false, length = 300)
    private String legalName;

    @Column(name = "trade_name", length = 300)
    private String tradeName;

    @Column(name = "legal_form_code", length = 64)
    private String legalFormCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "incorporation_country_code", nullable = false, columnDefinition = "char(2)")
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

    protected LegalEntityDetailsEntity() {
    }

    LegalEntityDetailsEntity(
            UUID partyId,
            LegalEntityDetails details,
            AuditInfo auditInfo) {
        this.partyId = partyId;
        this.legalName = details.legalName();
        this.tradeName = details.tradeName();
        this.legalFormCode = details.legalFormCode();
        this.incorporationCountryCode = details.incorporationCountryCode();
        this.incorporatedOn = details.incorporatedOn();
        this.dissolvedOn = details.dissolvedOn();
        this.createdAt = auditInfo.createdAt();
        this.createdBy = auditInfo.createdBy();
        this.updatedAt = auditInfo.updatedAt();
        this.updatedBy = auditInfo.updatedBy();
    }

    void attachTo(PartyEntity root) {
        party = root;
        partyId = root.id();
    }

    String legalName() {
        return legalName;
    }

    String tradeName() {
        return tradeName;
    }

    String legalFormCode() {
        return legalFormCode;
    }

    String incorporationCountryCode() {
        return incorporationCountryCode;
    }

    LocalDate incorporatedOn() {
        return incorporatedOn;
    }

    LocalDate dissolvedOn() {
        return dissolvedOn;
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
