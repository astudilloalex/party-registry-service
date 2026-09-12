package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps the aggregate root stored in the Flyway-managed {@code parties} table.
 */
@Entity
@Table(name = "parties")
public class PartyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "party_type")
    private PartyType type;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "record_status", nullable = false, columnDefinition = "party_record_status")
    private PartyRecordStatus recordStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToOne(mappedBy = "party", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private NaturalPersonDetailsEntity naturalPersonDetails;

    @OneToOne(mappedBy = "party", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private LegalEntityDetailsEntity legalEntityDetails;

    protected PartyEntity() {
    }

    PartyEntity(
            UUID id,
            UUID tenantId,
            PartyType type,
            String displayName,
            PartyRecordStatus recordStatus,
            AuditInfo auditInfo,
            long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.displayName = displayName;
        this.recordStatus = recordStatus;
        this.createdAt = auditInfo.createdAt();
        this.createdBy = auditInfo.createdBy();
        this.updatedAt = auditInfo.updatedAt();
        this.updatedBy = auditInfo.updatedBy();
        this.version = version;
    }

    void attachNaturalPersonDetails(NaturalPersonDetailsEntity details) {
        if (type != PartyType.NATURAL_PERSON) {
            throw new IllegalStateException("Natural-person details require a natural-person Party");
        }
        if (legalEntityDetails != null) {
            throw new IllegalStateException("Party already has legal-entity details");
        }
        naturalPersonDetails = details;
        details.attachTo(this);
    }

    void attachLegalEntityDetails(LegalEntityDetailsEntity details) {
        if (type != PartyType.LEGAL_ENTITY) {
            throw new IllegalStateException("Legal-entity details require a legal-entity Party");
        }
        if (naturalPersonDetails != null) {
            throw new IllegalStateException("Party already has natural-person details");
        }
        legalEntityDetails = details;
        details.attachTo(this);
    }

    void applyActivation(Party activated) {
        Objects.requireNonNull(activated, "activated");
        AuditInfo activatedAudit = activated.auditInfo();
        if (!id.equals(activated.partyId().value())
                || !tenantId.equals(activated.tenantId().value())
                || type != activated.type()
                || !displayName.equals(activated.displayName())
                || recordStatus != PartyRecordStatus.DRAFT
                || activated.recordStatus() != PartyRecordStatus.ACTIVE
                || version == Long.MAX_VALUE
                || activated.version().value() != version + 1
                || !createdAt.equals(activatedAudit.createdAt())
                || !createdBy.equals(activatedAudit.createdBy())) {
            throw new IllegalArgumentException("Activated Party does not match the persisted aggregate");
        }
        recordStatus = activated.recordStatus();
        updatedAt = activatedAudit.updatedAt();
        updatedBy = activatedAudit.updatedBy();
    }

    UUID id() {
        return id;
    }

    UUID tenantId() {
        return tenantId;
    }

    PartyType type() {
        return type;
    }

    String displayName() {
        return displayName;
    }

    PartyRecordStatus recordStatus() {
        return recordStatus;
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

    long version() {
        return version;
    }

    NaturalPersonDetailsEntity naturalPersonDetails() {
        return naturalPersonDetails;
    }

    LegalEntityDetailsEntity legalEntityDetails() {
        return legalEntityDetails;
    }

}
