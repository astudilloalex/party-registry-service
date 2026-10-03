package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        var schemeId = new IdentifierSchemeId(UUID.randomUUID());
        var version1 = PartyVersion.initial();
        var version2 = version1.next();

        List<FailureDescription> descriptions = List.of(
                new FailureDescription(new ApplicationFailure.IdentifierSchemeNotFound(), "Identifier scheme not found"),
                new FailureDescription(new ApplicationFailure.IdentifierSchemeCodeConflict(), "Identifier scheme code conflict"),
                new FailureDescription(new ApplicationFailure.IdentifierSchemeVersionMismatch(), "Identifier scheme version mismatch"),
                new FailureDescription(new ApplicationFailure.IdentifierSchemeRulesLocked(), "Identifier scheme processing properties are locked"),
                new FailureDescription(new ApplicationFailure.IdentifierSchemeRetired(), "Identifier scheme is retired"),
                new FailureDescription(new ApplicationFailure.InvalidIdentifierSchemeLifecycle(), "Invalid identifier scheme lifecycle transition"),
                new FailureDescription(new ApplicationFailure.IdentifierSchemeVersionExhausted(), "Identifier scheme version is exhausted"),
                new FailureDescription(new ApplicationFailure.IdentifierSchemeLengthRangeInvalid(), "Invalid identifier scheme length range"),
                new FailureDescription(new ApplicationFailure.InvalidIdentifierSchemeConfiguration(), "Invalid identifier scheme configuration"),
                new FailureDescription(new ApplicationFailure.InvalidIdentifierSchemeCursor(), "Invalid identifier scheme cursor"),
                new FailureDescription(
                        new ApplicationFailure.InvalidPartyCursor(),
                        "Invalid Party cursor"),
                new FailureDescription(new ApplicationFailure.InvalidNationalityCursor(), "Invalid nationality cursor"),
                new FailureDescription(new ApplicationFailure.NationalityNotFound(), "Nationality not found"),
                new FailureDescription(new ApplicationFailure.UnrecognizedNationalityCountry(),
                        "Unrecognized nationality country"),
                new FailureDescription(new ApplicationFailure.NationalityValidityInvalid(),
                        "Invalid nationality validity period"),
                new FailureDescription(new ApplicationFailure.NationalityValidityConflict(),
                        "Nationality validity conflict"),
                new FailureDescription(new ApplicationFailure.PrimaryNationalityConflict(),
                        "Primary nationality conflict"),
                new FailureDescription(
                        new ApplicationFailure.NationalityNotEffective(new NationalityId(UUID.randomUUID())),
                        "Nationality is not effective"),
                new FailureDescription(
                        new ApplicationFailure.NaturalPersonNotFound(partyId, tenantId),
                        "Natural person not found"),
                new FailureDescription(
                        new ApplicationFailure.LegalEntityNotFound(partyId, tenantId),
                        "Legal entity not found"),
                new FailureDescription(
                        new ApplicationFailure.IdempotencyKeyConflict("key-123"),
                        "Idempotency key conflict"),
                new FailureDescription(
                        new ApplicationFailure.ExpectedVersionMismatch(version1, version2),
                        "Expected version mismatch"),
                new FailureDescription(
                        new ApplicationFailure.UnrecognizedBirthCountry("ZZ"),
                        "Unrecognized birth country"),
                new FailureDescription(
                        new ApplicationFailure.UnrecognizedIncorporationCountry("ZZ"),
                        "Unrecognized incorporation country"),
                new FailureDescription(
                        new ApplicationFailure.UnknownIdentifierScheme("NATIONAL_ID"),
                        "Unknown identifier scheme"),
                new FailureDescription(
                        new ApplicationFailure.InactiveIdentifierScheme(schemeId),
                        "Inactive identifier scheme"),
                new FailureDescription(
                        new ApplicationFailure.IncompatibleIdentifierScheme(
                                schemeId,
                                PartyType.NATURAL_PERSON),
                        "Incompatible identifier scheme"),
                new FailureDescription(
                        new ApplicationFailure.IdentifierValidationFailure(
                                DomainViolation.IDENTIFIER_EXPIRED),
                        "Identifier validation failed"),
                new FailureDescription(
                        new ApplicationFailure.IdentifierUniquenessConflict(schemeId),
                        "Identifier uniqueness conflict"),
                new FailureDescription(
                        new ApplicationFailure.PartyNotFound(partyId, tenantId),
                        "Party not found"),
                new FailureDescription(
                        new ApplicationFailure.InvalidPartyLifecycle(
                                partyId,
                                PartyRecordStatus.ACTIVE),
                        "Invalid Party lifecycle transition"),
                new FailureDescription(
                        new ApplicationFailure.StalePartyVersion(version1, version2),
                        "Stale Party version"),
                new FailureDescription(
                        new ApplicationFailure.MissingQualifyingIdentifier(partyId),
                        "Qualifying Party identifier required"),
                new FailureDescription(
                        new ApplicationFailure.DependencyUnavailable("SERVICE_TIMEOUT"),
                        "Dependency unavailable"),
                new FailureDescription(
                        new ApplicationFailure.PersistenceFailure(),
                        "Persistence operation failed"),
                new FailureDescription(
                        new ApplicationFailure.IdentifierCatalogFailure(),
                        "Identifier catalog is internally inconsistent"),
                new FailureDescription(
                        new ApplicationFailure.InvalidBusinessState(DomainViolation.PARTY_ID_REQUIRED),
                        "Invalid business state"));

        assertEquals(ApplicationFailure.class.getPermittedSubclasses().length, descriptions.size());
        descriptions.forEach(description -> assertEquals(
                description.message(),
                new ApplicationException(description.failure()).getMessage()));
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

    /** Associates one sealed failure variant with its sanitized description. */
    private record FailureDescription(ApplicationFailure failure, String message) {
    }
}
