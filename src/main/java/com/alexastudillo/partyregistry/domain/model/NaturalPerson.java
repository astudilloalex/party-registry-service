package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents one tenant-scoped party permanently classified as a natural
 * person.
 */
public final class NaturalPerson {

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
                DETAILS_REQUIRED_MESSAGE);
        requiredDetails.validateAt(evaluatedOn);
        String effectiveDisplayName = displayName == null
                ? requiredDetails.derivedDisplayName()
                : displayName;
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
                "Replacement details are required");
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
                select(patch.givenNames(), details.givenNames()),
                select(patch.familyNames(), details.familyNames()),
                select(patch.preferredName(), details.preferredName()),
                select(patch.birthDate(), details.birthDate()),
                select(patch.dateOfDeath(), details.dateOfDeath()),
                select(patch.birthCountryCode(), details.birthCountryCode()));
        patchedDetails.validateAt(evaluatedOn);
        String patchedDisplayName = namesChanged(patchedDetails)
                ? patchedDetails.derivedDisplayName()
                : displayName;
        return copyWith(
                patchedDisplayName,
                patchedDetails,
                auditInfo.updated(occurredAt, updatedBy));
    }

    public PartyId partyId() {
        return partyId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PartyType type() {
        return PartyType.NATURAL_PERSON;
    }

    public String displayName() {
        return displayName;
    }

    public PartyRecordStatus recordStatus() {
        return recordStatus;
    }

    public PartyVersion version() {
        return version;
    }

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
        return !Objects.equals(details.givenNames(), candidate.givenNames())
                || !Objects.equals(details.familyNames(), candidate.familyNames());
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

    private static <T> @Nullable T select(FieldUpdate<T> update, @Nullable T currentValue) {
        return update.isPresent() ? update.value() : currentValue;
    }

    private static <T> T require(T value, DomainViolation violation, String message) {
        if (value == null) {
            throw new DomainValidationException(violation, message);
        }
        return value;
    }
}
