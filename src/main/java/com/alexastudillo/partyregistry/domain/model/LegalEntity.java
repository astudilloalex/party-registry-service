package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

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

    /**
     * Replaces legal details while preserving identity, lifecycle and the pre-persistence version.
     *
     * @param replacement complete legal-detail representation
     * @param evaluatedOn trusted UTC date used to validate the resulting legal history
     * @param occurredAt update timestamp
     * @param updatedBy validated modifying user
     * @return the updated candidate; persistence advances its version when accepted
     * @throws DomainValidationException when details, dates or audit information are invalid
     */
    public LegalEntity replaceDetails(
            LegalEntityDetails replacement, LocalDate evaluatedOn, Instant occurredAt, String updatedBy) {
        LegalEntityDetails normalized = require(replacement,
                DomainViolation.LEGAL_ENTITY_DETAILS_REQUIRED, DETAILS_REQUIRED_MESSAGE).normalizedForWrite();
        validateWriteLength(normalized.legalName(), 300, DomainViolation.LEGAL_NAME_TOO_LONG);
        validateWriteLength(normalized.tradeName(), 300, DomainViolation.TRADE_NAME_TOO_LONG);
        validateWriteLength(normalized.legalFormCode(), 64, DomainViolation.LEGAL_FORM_CODE_TOO_LONG);
        normalized.validateAt(evaluatedOn);
        return withUpdatedDetails(normalized, occurredAt, updatedBy);
    }

    /**
     * Applies supplied fields only and validates the complete resulting legal history.
     *
     * @param patch presence-aware legal-detail changes
     * @param evaluatedOn trusted UTC evaluation date
     * @param occurredAt update timestamp
     * @param updatedBy validated modifying user
     * @return the updated candidate, retaining omitted historical representations
     * @throws DomainValidationException when the patch, merged details or audit information are invalid
     */
    public LegalEntity patchDetails(
            LegalEntityPatch patch, LocalDate evaluatedOn, Instant occurredAt, String updatedBy) {
        require(patch, DomainViolation.PATCH_REQUIRED, "Legal-detail patch is required");
        if (patch.isEmpty()) {
            throw new DomainValidationException(DomainViolation.EMPTY_PATCH, "Patch must contain at least one field");
        }
        if (patch.legalName().isPresent() && patch.legalName().value() == null) {
            throw new DomainValidationException(DomainViolation.LEGAL_NAME_REQUIRED, "Legal name cannot be cleared");
        }
        if (patch.incorporationCountryCode().isPresent() && patch.incorporationCountryCode().value() == null) {
            throw new DomainValidationException(DomainViolation.INCORPORATION_COUNTRY_CODE_REQUIRED,
                    "Incorporation country cannot be cleared");
        }
        LegalEntityDetails merged = new LegalEntityDetails(
                selectText(patch.legalName(), details.legalName(), 300, DomainViolation.LEGAL_NAME_TOO_LONG),
                selectText(patch.tradeName(), details.tradeName(), 300, DomainViolation.TRADE_NAME_TOO_LONG),
                selectText(patch.legalFormCode(), details.legalFormCode(), 64, DomainViolation.LEGAL_FORM_CODE_TOO_LONG),
                patch.incorporationCountryCode().isPresent()
                        ? PartyTextNormalization.countryCode(patch.incorporationCountryCode().value())
                        : details.incorporationCountryCode(),
                select(patch.incorporatedOn(), details.incorporatedOn()),
                select(patch.dissolvedOn(), details.dissolvedOn()));
        merged.validateAt(evaluatedOn);
        return withUpdatedDetails(merged, occurredAt, updatedBy);
    }

    private LegalEntity withUpdatedDetails(LegalEntityDetails updated, Instant occurredAt, String updatedBy) {
        boolean legalNameChanged = !Objects.equals(
                PartyTextNormalization.uppercase(details.legalName()),
                PartyTextNormalization.uppercase(updated.legalName()));
        return new LegalEntity(partyId, tenantId,
                legalNameChanged ? updated.derivedDisplayName() : displayName,
                recordStatus, version, auditInfo.updated(occurredAt, updatedBy), updated);
    }

    private static @Nullable String selectText(
            FieldUpdate<String> update, @Nullable String current, int maximumLength, DomainViolation violation) {
        if (!update.isPresent()) {
            return current;
        }
        String normalized = PartyTextNormalization.uppercase(update.value());
        validateWriteLength(normalized, maximumLength, violation);
        return normalized;
    }

    private static void validateWriteLength(@Nullable String value, int maximumLength, DomainViolation violation) {
        // New-write limits count UTF-16 units; restoration keeps its existing historical contract.
        if (value != null && value.length() > maximumLength) {
            throw new DomainValidationException(violation, "Legal-detail text exceeds the normalized length limit");
        }
    }

    private static <T> @Nullable T select(FieldUpdate<T> update, @Nullable T current) {
        return update.isPresent() ? update.value() : current;
    }

    @Override
    public LegalEntity correctDisplayName(String value, Instant occurredAt, String updatedBy) {
        String normalized = validateDisplayName(PartyTextNormalization.uppercase(value));
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new DomainValidationException(DomainViolation.DISPLAY_NAME_TOO_LONG,
                    "Display name exceeds the normalized UTF-16 length limit");
        }
        return new LegalEntity(partyId, tenantId, normalized, recordStatus, version.next(),
                auditInfo.updated(occurredAt, updatedBy), details);
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
    public LegalEntity deactivate(Instant occurredAt, String updatedBy) {
        if (recordStatus != PartyRecordStatus.ACTIVE) {
            throw new DomainValidationException(DomainViolation.PARTY_DEACTIVATION_INVALID_STATE,
                    "Only an active Party can be deactivated");
        }
        return new LegalEntity(partyId, tenantId, displayName, PartyRecordStatus.INACTIVE,
                version.next(), auditInfo.updated(occurredAt, updatedBy), details);
    }

    @Override
    public LegalEntity archive(Instant occurredAt, String updatedBy) {
        if (recordStatus == PartyRecordStatus.ARCHIVED) {
            throw new DomainValidationException(DomainViolation.PARTY_ARCHIVAL_INVALID_STATE,
                    "An archived Party cannot be archived again");
        }
        return new LegalEntity(partyId, tenantId, displayName, PartyRecordStatus.ARCHIVED,
                version.next(), auditInfo.updated(occurredAt, updatedBy), details);
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

    private static String validateDisplayName(@Nullable String value) {
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
