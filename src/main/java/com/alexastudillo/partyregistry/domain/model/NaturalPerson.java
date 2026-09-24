package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents one tenant-scoped party permanently classified as a natural
 * person.
 */
public final class NaturalPerson implements Party {

    private static final int MAX_DISPLAY_NAME_LENGTH = 300;
    private static final String DETAILS_REQUIRED_MESSAGE = "Natural-person details are required";

    private final PartyId partyId;
    private final TenantId tenantId;
    private final String displayName;
    private final PartyRecordStatus recordStatus;
    private final PartyVersion version;
    private final AuditInfo auditInfo;
    private final NaturalPersonDetails details;

    private NaturalPerson(
            PartyId partyId,
            TenantId tenantId,
            String displayName,
            PartyRecordStatus recordStatus,
            PartyVersion version,
            AuditInfo auditInfo,
            NaturalPersonDetails details) {
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
                DomainViolation.NATURAL_PERSON_DETAILS_REQUIRED,
                DETAILS_REQUIRED_MESSAGE);
    }

    /**
     * Creates a new draft natural person at version zero.
     *
     * @param partyId     generated party identifier
     * @param tenantId    owning tenant
     * @param displayName explicit display name, or null to derive it from the names
     * @param details     natural-person details
     * @param evaluatedOn request evaluation date
     * @param occurredAt  creation timestamp
     * @param createdBy   creating user
     * @return a valid natural-person aggregate
     * @throws DomainValidationException when any creation invariant is violated
     */
    public static NaturalPerson create(
            PartyId partyId,
            TenantId tenantId,
            @Nullable String displayName,
            NaturalPersonDetails details,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String createdBy) {
        NaturalPersonDetails requiredDetails = require(
                details,
                DomainViolation.NATURAL_PERSON_DETAILS_REQUIRED,
                DETAILS_REQUIRED_MESSAGE).normalizedForWrite();
        requiredDetails.validateAt(evaluatedOn);
        String effectiveDisplayName = displayName == null
                ? requiredDetails.derivedDisplayName()
                : PartyTextNormalization.uppercase(displayName);
        return new NaturalPerson(
                partyId,
                tenantId,
                effectiveDisplayName,
                PartyRecordStatus.DRAFT,
                PartyVersion.initial(),
                AuditInfo.initial(occurredAt, createdBy),
                requiredDetails);
    }

    /**
     * Rehydrates a persisted natural person while enforcing aggregate invariants.
     *
     * @param partyId      persisted party identifier
     * @param tenantId     owning tenant
     * @param displayName  persisted display name
     * @param recordStatus persisted lifecycle status
     * @param version      persisted aggregate version
     * @param auditInfo    persisted audit information
     * @param details      persisted natural-person details
     * @return the restored natural-person aggregate
     * @throws DomainValidationException when persisted state violates an aggregate
     *                                   invariant
     */
    public static NaturalPerson restore(
            PartyId partyId,
            TenantId tenantId,
            String displayName,
            PartyRecordStatus recordStatus,
            PartyVersion version,
            AuditInfo auditInfo,
            NaturalPersonDetails details) {
        NaturalPersonDetails requiredDetails = require(
                details,
                DomainViolation.NATURAL_PERSON_DETAILS_REQUIRED,
                DETAILS_REQUIRED_MESSAGE);
        return new NaturalPerson(
                partyId,
                tenantId,
                displayName,
                recordStatus,
                version,
                auditInfo,
                requiredDetails);
    }

    /**
     * Replaces the complete natural-person detail representation.
     *
     * @param replacement complete replacement details
     * @param evaluatedOn operation evaluation date
     * @param occurredAt  modification timestamp
     * @param updatedBy   modifying user
     * @return a new aggregate containing the replacement
     * @throws DomainValidationException when the replacement or audit transition is
     *                                   invalid
     */
    public NaturalPerson replaceDetails(
            NaturalPersonDetails replacement,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String updatedBy) {
        NaturalPersonDetails requiredReplacement = require(
                replacement,
                DomainViolation.NATURAL_PERSON_DETAILS_REQUIRED,
                "Replacement details are required").normalizedForWrite();
        requiredReplacement.validateAt(evaluatedOn);
        String replacementDisplayName = namesChanged(requiredReplacement)
                ? requiredReplacement.derivedDisplayName()
                : displayName;
        return copyWith(
                replacementDisplayName,
                requiredReplacement,
                auditInfo.updated(occurredAt, updatedBy));
    }

    /**
     * Applies only present patch fields and validates the complete resulting state.
     *
     * @param patch       presence-aware field changes
     * @param evaluatedOn operation evaluation date
     * @param occurredAt  modification timestamp
     * @param updatedBy   modifying user
     * @return a new aggregate containing the patch result
     * @throws DomainValidationException when the patch or resulting state is
     *                                   invalid
     */
    public NaturalPerson patchDetails(
            NaturalPersonPatch patch,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String updatedBy) {
        if (patch == null) {
            throw new DomainValidationException(DomainViolation.PATCH_REQUIRED, "Patch is required");
        }
        if (patch.isEmpty()) {
            throw new DomainValidationException(
                    DomainViolation.EMPTY_PATCH,
                    "Patch must contain at least one field");
        }
        if (patch.givenNames().isPresent() && patch.givenNames().value() == null) {
            throw new DomainValidationException(
                    DomainViolation.GIVEN_NAMES_REQUIRED,
                    "Given names cannot be cleared");
        }
        if (patch.familyNames().isPresent() && patch.familyNames().value() == null) {
            throw new DomainValidationException(
                    DomainViolation.FAMILY_NAMES_REQUIRED,
                    "Family names cannot be cleared");
        }

        NaturalPersonDetails patchedDetails = new NaturalPersonDetails(
                selectText(patch.givenNames(), details.givenNames()),
                selectText(patch.familyNames(), details.familyNames()),
                selectText(patch.preferredName(), details.preferredName()),
                select(patch.birthDate(), details.birthDate()),
                select(patch.dateOfDeath(), details.dateOfDeath()),
                patch.birthCountryCode().isPresent()
                        ? PartyTextNormalization.countryCode(patch.birthCountryCode().value())
                        : details.birthCountryCode());
        patchedDetails.validateAt(evaluatedOn);
        String patchedDisplayName = namesChanged(patchedDetails)
                ? patchedDetails.derivedDisplayName()
                : displayName;
        return copyWith(
                patchedDisplayName,
                patchedDetails,
                auditInfo.updated(occurredAt, updatedBy));
    }

    @Override
    public NaturalPerson correctDisplayName(String value, Instant occurredAt, String updatedBy) {
        String normalized = validateDisplayName(PartyTextNormalization.uppercase(value));
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new DomainValidationException(DomainViolation.DISPLAY_NAME_TOO_LONG,
                    "Display name exceeds the normalized UTF-16 length limit");
        }
        return new NaturalPerson(partyId, tenantId, normalized, recordStatus, version.next(),
                auditInfo.updated(occurredAt, updatedBy), details);
    }

    @Override
    public NaturalPerson activate(Instant occurredAt, String updatedBy) {
        if (recordStatus != PartyRecordStatus.DRAFT) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_ACTIVATION_INVALID_STATE,
                    "Only a draft Party can be activated");
        }
        return new NaturalPerson(
                partyId,
                tenantId,
                displayName,
                PartyRecordStatus.ACTIVE,
                version.next(),
                auditInfo.updated(occurredAt, updatedBy),
                details);
    }

    @Override
    public NaturalPerson deactivate(Instant occurredAt, String updatedBy) {
        if (recordStatus != PartyRecordStatus.ACTIVE) {
            throw new DomainValidationException(DomainViolation.PARTY_DEACTIVATION_INVALID_STATE,
                    "Only an active Party can be deactivated");
        }
        return new NaturalPerson(partyId, tenantId, displayName, PartyRecordStatus.INACTIVE,
                version.next(), auditInfo.updated(occurredAt, updatedBy), details);
    }

    @Override
    public NaturalPerson archive(Instant occurredAt, String updatedBy) {
        if (recordStatus == PartyRecordStatus.ARCHIVED) {
            throw new DomainValidationException(DomainViolation.PARTY_ARCHIVAL_INVALID_STATE,
                    "An archived Party cannot be archived again");
        }
        return new NaturalPerson(partyId, tenantId, displayName, PartyRecordStatus.ARCHIVED,
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
        return PartyType.NATURAL_PERSON;
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

    public NaturalPersonDetails details() {
        return details;
    }

    private NaturalPerson copyWith(
            String newDisplayName,
            NaturalPersonDetails newDetails,
            AuditInfo newAuditInfo) {
        return new NaturalPerson(
                partyId,
                tenantId,
                newDisplayName,
                recordStatus,
                version,
                newAuditInfo,
                newDetails);
    }

    private boolean namesChanged(NaturalPersonDetails candidate) {
        return !Objects.equals(PartyTextNormalization.uppercase(details.givenNames()),
                        PartyTextNormalization.uppercase(candidate.givenNames()))
                || !Objects.equals(PartyTextNormalization.uppercase(details.familyNames()),
                        PartyTextNormalization.uppercase(candidate.familyNames()));
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

    private static <T> @Nullable T select(FieldUpdate<T> update, @Nullable T currentValue) {
        return update.isPresent() ? update.value() : currentValue;
    }

    private static @Nullable String selectText(FieldUpdate<String> update, @Nullable String currentValue) {
        return update.isPresent() ? PartyTextNormalization.uppercase(update.value()) : currentValue;
    }

    private static <T> T require(T value, DomainViolation violation, String message) {
        if (value == null) {
            throw new DomainValidationException(violation, message);
        }
        return value;
    }
}
