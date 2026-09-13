package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents one tenant-scoped Party permanently classified as a legal entity.
 */
public final class LegalEntity implements Party {

    private static final int MAX_DISPLAY_NAME_LENGTH = 300;
    private static final String DETAILS_REQUIRED_MESSAGE = "Legal-entity details are required";

    private final PartyId partyId;
    private final TenantId tenantId;
    private final String displayName;
    private final PartyRecordStatus recordStatus;
    private final PartyVersion version;
    private final AuditInfo auditInfo;
    private final LegalEntityDetails details;

    private LegalEntity(
            PartyId partyId,
            TenantId tenantId,
            String displayName,
            PartyRecordStatus recordStatus,
            PartyVersion version,
            AuditInfo auditInfo,
            LegalEntityDetails details) {
        this.partyId = require(partyId, DomainViolation.PARTY_ID_REQUIRED, "Party identifier is required");
        this.tenantId = require(tenantId, DomainViolation.TENANT_ID_REQUIRED, "Tenant identifier is required");
        this.displayName = validateDisplayName(displayName);
        this.recordStatus = require(
                recordStatus,
                DomainViolation.PARTY_STATUS_REQUIRED,
                "Party record status is required");
        this.version = require(version, DomainViolation.PARTY_VERSION_REQUIRED, "Party version is required");
        this.auditInfo = require(auditInfo, DomainViolation.AUDIT_REQUIRED, "Audit information is required");
        this.details = require(
                details,
                DomainViolation.LEGAL_ENTITY_DETAILS_REQUIRED,
                DETAILS_REQUIRED_MESSAGE);
    }

    /**
     * Creates a new draft legal entity at version zero.
     *
     * @param partyId generated Party identifier
     * @param tenantId owning tenant
     * @param displayName explicit display name, or null to derive it from the legal name
     * @param details legal-entity details
     * @param evaluatedOn request evaluation date
     * @param occurredAt creation timestamp
     * @param createdBy creating user
     * @return a valid legal-entity aggregate
     */
    public static LegalEntity create(
            PartyId partyId,
            TenantId tenantId,
            @Nullable String displayName,
            LegalEntityDetails details,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String createdBy) {
        LegalEntityDetails requiredDetails = require(
                details,
                DomainViolation.LEGAL_ENTITY_DETAILS_REQUIRED,
                DETAILS_REQUIRED_MESSAGE).normalizedForWrite();
        requiredDetails.validateAt(evaluatedOn);
        String effectiveDisplayName = displayName == null
                ? requiredDetails.derivedDisplayName()
                : PartyTextNormalization.uppercase(displayName);
        return new LegalEntity(
                partyId,
                tenantId,
                effectiveDisplayName,
                PartyRecordStatus.DRAFT,
                PartyVersion.initial(),
                AuditInfo.initial(occurredAt, createdBy),
                requiredDetails);
    }

    /**
     * Rehydrates a persisted legal entity while enforcing aggregate invariants.
     *
     * @return the restored legal-entity aggregate
     */
    public static LegalEntity restore(
            PartyId partyId,
            TenantId tenantId,
            String displayName,
            PartyRecordStatus recordStatus,
            PartyVersion version,
            AuditInfo auditInfo,
            LegalEntityDetails details) {
        return new LegalEntity(
                partyId,
                tenantId,
                displayName,
                recordStatus,
                version,
                auditInfo,
                require(details, DomainViolation.LEGAL_ENTITY_DETAILS_REQUIRED, DETAILS_REQUIRED_MESSAGE));
    }

    @Override
    public LegalEntity activate(Instant occurredAt, String updatedBy) {
        if (recordStatus != PartyRecordStatus.DRAFT) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_ACTIVATION_INVALID_STATE,
                    "Only a draft Party can be activated");
        }
        return new LegalEntity(
                partyId,
                tenantId,
                displayName,
                PartyRecordStatus.ACTIVE,
                version.next(),
                auditInfo.updated(occurredAt, updatedBy),
                details);
    }

    @Override
    public PartyId partyId() {
        return partyId;
    }

    @Override
    public TenantId tenantId() {
        return tenantId;
    }

    @Override
    public PartyType type() {
        return PartyType.LEGAL_ENTITY;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public PartyRecordStatus recordStatus() {
        return recordStatus;
    }

    @Override
    public PartyVersion version() {
        return version;
    }

    @Override
    public AuditInfo auditInfo() {
        return auditInfo;
    }

    public LegalEntityDetails details() {
        return details;
    }

    private static String validateDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(
                    DomainViolation.DISPLAY_NAME_REQUIRED,
                    "Display name is required");
        }
        if (value.codePointCount(0, value.length()) > MAX_DISPLAY_NAME_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.DISPLAY_NAME_TOO_LONG,
                    "Display name exceeds the maximum length");
        }
        return value;
    }

    private static <T> T require(T value, DomainViolation violation, String message) {
        if (value == null) {
            throw new DomainValidationException(violation, message);
        }
        return value;
    }
}
