package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.util.Objects;

/**
 * Describes the transport-neutral failures emitted by application operations.
 */
public sealed interface ApplicationFailure {

    /**
     * The requested natural person is absent, belongs to another tenant, or is
     * not classified as a natural person.
     */
    record NaturalPersonNotFound(PartyId partyId, TenantId tenantId) implements ApplicationFailure {

        public NaturalPersonNotFound {
            Objects.requireNonNull(partyId, "partyId");
            Objects.requireNonNull(tenantId, "tenantId");
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
     * The requested operation violates a business invariant of the resulting
     * natural-person state.
     */
    record InvalidBusinessState(DomainViolation violation) implements ApplicationFailure {

        public InvalidBusinessState {
            Objects.requireNonNull(violation, "violation");
        }
    }
}
