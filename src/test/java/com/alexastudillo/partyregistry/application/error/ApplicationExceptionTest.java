package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validates the behavior, message generation, and failure wrapping of
 * {@link ApplicationException}.
 */
class ApplicationExceptionTest {

    @Test
    void shouldDescribeAllFailureVariantsCorrectly() {
        var partyId = new PartyId(UUID.randomUUID());
        var tenantId = new TenantId(UUID.randomUUID());
        var version1 = PartyVersion.initial();
        var version2 = version1.next();

        var notFound = new ApplicationFailure.NaturalPersonNotFound(partyId, tenantId);
        assertEquals("Natural person not found", new ApplicationException(notFound).getMessage());

        var conflict = new ApplicationFailure.IdempotencyKeyConflict("key-123");
        assertEquals("Idempotency key conflict", new ApplicationException(conflict).getMessage());

        var mismatch = new ApplicationFailure.ExpectedVersionMismatch(version1, version2);
        assertEquals("Expected version mismatch", new ApplicationException(mismatch).getMessage());

        var country = new ApplicationFailure.UnrecognizedBirthCountry("ZZ");
        assertEquals("Unrecognized birth country", new ApplicationException(country).getMessage());

        var unavailable = new ApplicationFailure.DependencyUnavailable("SERVICE_TIMEOUT");
        assertEquals("Dependency unavailable", new ApplicationException(unavailable).getMessage());

        var persistenceFailure = new ApplicationFailure.PersistenceFailure();
        assertEquals("Persistence operation failed", new ApplicationException(persistenceFailure).getMessage());

        var invalidState = new ApplicationFailure.InvalidBusinessState(DomainViolation.PARTY_ID_REQUIRED);
        assertEquals("Invalid business state", new ApplicationException(invalidState).getMessage());
    }

    @Test
    void shouldWrapDomainValidationExceptionProperly() {
        var domainException = new DomainValidationException(DomainViolation.PARTY_ID_REQUIRED,
                "Party identifier is required");
        var appException = ApplicationException.of(domainException);

        assertSame(domainException, appException.getCause());
        assertEquals("Invalid business state", appException.getMessage());
        if (appException.failure() instanceof ApplicationFailure.InvalidBusinessState invalidState) {
            assertEquals(DomainViolation.PARTY_ID_REQUIRED, invalidState.violation());
        }
    }

    @Test
    void shouldRequireNonNullArguments() {
        var cause = new RuntimeException();
        assertThrows(NullPointerException.class, () -> new ApplicationException(null));
        assertThrows(NullPointerException.class, () -> new ApplicationException(null, cause));
        assertThrows(NullPointerException.class, () -> ApplicationException.of(null));
    }
}
