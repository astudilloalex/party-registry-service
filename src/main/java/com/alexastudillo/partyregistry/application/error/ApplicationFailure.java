package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Describes the transport-neutral failures emitted by application operations.
 */
public sealed interface ApplicationFailure {

    /** Reports absence in the global catalog independently of identifier admission. */
    record IdentifierSchemeNotFound() implements ApplicationFailure { }

    /** Reports an exact catalog code already assigned to any global scheme. */
    record IdentifierSchemeCodeConflict() implements ApplicationFailure { }

    /** Reports a stale catalog precondition or a losing independent scheme write. */
    record IdentifierSchemeVersionMismatch() implements ApplicationFailure { }

    /** Rejects any supplied processing-property edit after activation. */
    record IdentifierSchemeRulesLocked() implements ApplicationFailure { }

    /** Rejects maintenance of a terminal retired catalog entry. */
    record IdentifierSchemeRetired() implements ApplicationFailure { }

    /** Rejects a new action outside the permitted scheme state/action matrix. */
    record InvalidIdentifierSchemeLifecycle() implements ApplicationFailure { }

    /** Reports that an otherwise permitted catalog mutation cannot advance its version. */
    record IdentifierSchemeVersionExhausted() implements ApplicationFailure { }

    /** Rejects out-of-range or incoherent resulting catalog length bounds. */
    record IdentifierSchemeLengthRangeInvalid() implements ApplicationFailure { }

    /** Rejects processing configuration that cannot be admitted by the supported local rule catalog. */
    record InvalidIdentifierSchemeConfiguration() implements ApplicationFailure { }

    /** Rejects malformed, altered, foreign-resource, or cross-scope scheme navigation. */
    record InvalidIdentifierSchemeCursor() implements ApplicationFailure { }

    /** Reports an invalid continuation without retaining or exposing client cursor material. */
    record InvalidPartyCursor() implements ApplicationFailure {
    }

    /** Reports a malformed, altered, or cross-scope nationality continuation. */
    record InvalidNationalityCursor() implements ApplicationFailure {
    }

    /** Conceals a missing nationality or one belonging to another Party or tenant. */
    record NationalityNotFound() implements ApplicationFailure {
    }

    /** Reports a country code definitely absent from the geographic reference. */
    record UnrecognizedNationalityCountry() implements ApplicationFailure {
    }

    /** Rejects an interval with its end before its start. */
    record NationalityValidityInvalid() implements ApplicationFailure {
    }

    /** Rejects overlapping inclusive intervals for the same country and Party. */
    record NationalityValidityConflict() implements ApplicationFailure {
    }

    /** Rejects overlapping primary intervals for the same Party. */
    record PrimaryNationalityConflict() implements ApplicationFailure {
    }

    /** Reports a target not effective on the operation's captured evaluation date. */
    record NationalityNotEffective(NationalityId nationalityId) implements ApplicationFailure {
        public NationalityNotEffective {
            Objects.requireNonNull(nationalityId, "nationalityId");
        }
    }

    /**
     * The requested natural person is absent, belongs to another tenant, or is
     * not classified as a natural person.
     */
    record NaturalPersonNotFound(PartyId partyId, TenantId tenantId) implements ApplicationFailure {

        public NaturalPersonNotFound {
            requirePartyId(partyId);
            requireTenantId(tenantId);
        }
    }

    /** Reports an absent, cross-tenant, or wrong-type legal-entity lookup without distinguishing them. */
    record LegalEntityNotFound(PartyId partyId, TenantId tenantId) implements ApplicationFailure {

        public LegalEntityNotFound {
            requirePartyId(partyId);
            requireTenantId(tenantId);
        }
    }

    /**
     * An idempotency key already identifies a completed creation with a
     * different effective request.
     */
    record IdempotencyKeyConflict(String idempotencyKey) implements ApplicationFailure {

        public IdempotencyKeyConflict {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency key is required");
            }
        }
    }

    /**
     * The expected aggregate version does not match the current version.
     */
    record ExpectedVersionMismatch(
            PartyVersion expectedVersion,
            PartyVersion currentVersion) implements ApplicationFailure {

        public ExpectedVersionMismatch {
            Objects.requireNonNull(expectedVersion, "expectedVersion");
            Objects.requireNonNull(currentVersion, "currentVersion");
        }
    }

    /**
     * The Geographic Reference Service does not recognize a supplied birth
     * country code.
     */
    record UnrecognizedBirthCountry(String birthCountryCode) implements ApplicationFailure {

        public UnrecognizedBirthCountry {
            if (birthCountryCode == null || birthCountryCode.isBlank()) {
                throw new IllegalArgumentException("Birth country code is required");
            }
        }
    }

    /**
     * The Geographic Reference Service does not recognize a supplied incorporation
     * country code.
     */
    record UnrecognizedIncorporationCountry(String incorporationCountryCode) implements ApplicationFailure {

        public UnrecognizedIncorporationCountry {
            if (incorporationCountryCode == null || incorporationCountryCode.isBlank()) {
                throw new IllegalArgumentException("Incorporation country code is required");
            }
        }
    }

    /**
     * No identifier scheme exists for the submitted stable code.
     */
    record UnknownIdentifierScheme(String schemeCode) implements ApplicationFailure {

        public UnknownIdentifierScheme {
            requireText(schemeCode, "Identifier scheme code is required");
        }
    }

    /**
     * The resolved identifier scheme does not permit new registrations.
     */
    record InactiveIdentifierScheme(IdentifierSchemeId schemeId) implements ApplicationFailure {

        public InactiveIdentifierScheme {
            requireSchemeId(schemeId);
        }
    }

    /**
     * The resolved identifier scheme cannot identify the requested Party type.
     */
    record IncompatibleIdentifierScheme(
            IdentifierSchemeId schemeId,
            PartyType partyType) implements ApplicationFailure {

        public IncompatibleIdentifierScheme {
            requireSchemeId(schemeId);
            Objects.requireNonNull(partyType, "partyType");
        }
    }

    /**
     * The submitted identifier violates one of its semantic scheme rules.
     */
    record IdentifierValidationFailure(DomainViolation violation) implements ApplicationFailure {

        public IdentifierValidationFailure {
            Objects.requireNonNull(violation, "violation");
        }
    }

    /**
     * Another active identifier already owns the same tenant-scoped scheme value.
     */
    record IdentifierUniquenessConflict(IdentifierSchemeId schemeId) implements ApplicationFailure {

        public IdentifierUniquenessConflict {
            requireSchemeId(schemeId);
        }
    }

    /**
     * The requested Party is absent or belongs to another tenant.
     */
    record PartyNotFound(PartyId partyId, TenantId tenantId) implements ApplicationFailure {

        public PartyNotFound {
            requirePartyId(partyId);
            requireTenantId(tenantId);
        }
    }

    /**
     * The Party's current lifecycle state does not permit the requested transition.
     */
    record InvalidPartyLifecycle(
            PartyId partyId,
            PartyRecordStatus currentStatus) implements ApplicationFailure {

        public InvalidPartyLifecycle {
            requirePartyId(partyId);
            Objects.requireNonNull(currentStatus, "currentStatus");
        }
    }

    /**
     * The activation request used a Party version that is no longer current.
     */
    record StalePartyVersion(
            PartyVersion expectedVersion,
            PartyVersion currentVersion) implements ApplicationFailure {

        public StalePartyVersion {
            Objects.requireNonNull(expectedVersion, "expectedVersion");
            Objects.requireNonNull(currentVersion, "currentVersion");
        }
    }

    /**
     * The Party has no verified, compatible, non-expired identifier for activation.
     */
    record MissingQualifyingIdentifier(PartyId partyId) implements ApplicationFailure {

        public MissingQualifyingIdentifier {
            requirePartyId(partyId);
        }
    }

    /**
     * A required external dependency cannot currently complete the operation.
     */
    record DependencyUnavailable(String dependencyName) implements ApplicationFailure {

        public DependencyUnavailable {
            if (dependencyName == null || dependencyName.isBlank()) {
                throw new IllegalArgumentException("Dependency name is required");
            }
        }
    }

    /**
     * Persistence could not complete without exposing database-specific details.
     */
    record PersistenceFailure() implements ApplicationFailure {
    }

    /**
     * Active identifier-scheme data references an unsupported internal rule.
     */
    record IdentifierCatalogFailure() implements ApplicationFailure {
    }

    /**
     * The requested operation violates a business invariant of the resulting
     * natural-person state.
     */
    record InvalidBusinessState(DomainViolation violation) implements ApplicationFailure {

        public InvalidBusinessState {
            Objects.requireNonNull(violation, "violation");
        }
    }

    private static void requirePartyId(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
    }

    private static void requireTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
    }

    private static void requireSchemeId(IdentifierSchemeId schemeId) {
        Objects.requireNonNull(schemeId, "schemeId");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
