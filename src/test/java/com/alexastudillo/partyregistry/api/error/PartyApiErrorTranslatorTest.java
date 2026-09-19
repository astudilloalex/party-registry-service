package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the stable API translation of known application failures.
 */
class PartyApiErrorTranslatorTest {

    private static final UUID PARTY_ID = UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
    private static final UUID TENANT_ID = UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");

    private final PartyApiErrorTranslator translator = new PartyApiErrorTranslator();

    @Test
    void exposesTheOwnedCreationResponseCode() {
        assertEquals(201, PartyResponseCode.CREATED.getStatus());
        assertEquals("successful", PartyResponseCode.CREATED.getCode());
    }

    @Test
    void translatesLegalEntityAbsenceWithItsSpecificCode() {
        assertTranslation(new ApplicationFailure.LegalEntityNotFound(new PartyId(PARTY_ID), new TenantId(TENANT_ID)),
                PartyResponseCode.LEGAL_ENTITY_NOT_FOUND);
        assertEquals("legal-entity-not-found", PartyResponseCode.LEGAL_ENTITY_NOT_FOUND.getCode());
        assertEquals(404, PartyResponseCode.LEGAL_ENTITY_NOT_FOUND.getStatus());
    }

    @Test
    void translatesEveryKnownApplicationFailure() {
        IdentifierSchemeId schemeId = new IdentifierSchemeId(UUID.randomUUID());
        PartyId partyId = new PartyId(PARTY_ID);
        TenantId tenantId = new TenantId(TENANT_ID);
        assertTranslation(
                new ApplicationFailure.NaturalPersonNotFound(partyId, tenantId),
                PartyResponseCode.NATURAL_PERSON_NOT_FOUND);
        assertTranslation(
                new ApplicationFailure.IdempotencyKeyConflict("idempotency-key"),
                PartyResponseCode.IDEMPOTENCY_KEY_CONFLICT);
        assertTranslation(
                new ApplicationFailure.ExpectedVersionMismatch(new PartyVersion(1),
                        new PartyVersion(2)),
                PartyResponseCode.EXPECTED_VERSION_MISMATCH);
        assertTranslation(
                new ApplicationFailure.InvalidBusinessState(DomainViolation.DEATH_BEFORE_BIRTH),
                PartyResponseCode.DEATH_BEFORE_BIRTH);
        assertTranslation(
                new ApplicationFailure.UnrecognizedBirthCountry("ZZ"),
                PartyResponseCode.UNRECOGNIZED_BIRTH_COUNTRY);
        assertTranslation(
                new ApplicationFailure.UnrecognizedIncorporationCountry("ZZ"),
                PartyResponseCode.UNRECOGNIZED_INCORPORATION_COUNTRY);
        assertTranslation(
                new ApplicationFailure.DependencyUnavailable("geographic-reference"),
                PartyResponseCode.DEPENDENCY_UNAVAILABLE);
        assertTranslation(
                new ApplicationFailure.UnknownIdentifierScheme("NATIONAL_ID"),
                PartyResponseCode.UNKNOWN_IDENTIFIER_SCHEME);
        assertTranslation(
                new ApplicationFailure.InactiveIdentifierScheme(schemeId),
                PartyResponseCode.INACTIVE_IDENTIFIER_SCHEME);
        assertTranslation(
                new ApplicationFailure.IncompatibleIdentifierScheme(
                        schemeId,
                        PartyType.NATURAL_PERSON),
                PartyResponseCode.INCOMPATIBLE_IDENTIFIER_SCHEME);
        assertTranslation(
                new ApplicationFailure.IdentifierValidationFailure(
                        DomainViolation.IDENTIFIER_VALUE_INVALID),
                PartyResponseCode.IDENTIFIER_VALIDATION_FAILURE);
        assertTranslation(
                new ApplicationFailure.IdentifierUniquenessConflict(schemeId),
                PartyResponseCode.IDENTIFIER_UNIQUENESS_CONFLICT);
        assertTranslation(
                new ApplicationFailure.PartyNotFound(partyId, tenantId),
                PartyResponseCode.PARTY_NOT_FOUND);
        assertTranslation(
                new ApplicationFailure.InvalidPartyLifecycle(
                        partyId,
                        PartyRecordStatus.ACTIVE),
                PartyResponseCode.INVALID_PARTY_LIFECYCLE);
        assertTranslation(
                new ApplicationFailure.StalePartyVersion(
                        PartyVersion.initial(),
                        new PartyVersion(1)),
                PartyResponseCode.STALE_PARTY_VERSION);
        assertTranslation(
                new ApplicationFailure.MissingQualifyingIdentifier(partyId),
                PartyResponseCode.MISSING_QUALIFYING_IDENTIFIER);
    }

    @Test
    void preservesConflictStatusesAndIndividualBusinessCauses() {
        assertEquals(409, PartyResponseCode.IDENTIFIER_UNIQUENESS_CONFLICT.getStatus());
        assertEquals(409, PartyResponseCode.INVALID_PARTY_LIFECYCLE.getStatus());
        Map<DomainViolation, PartyResponseCode> causes = Map.of(
                DomainViolation.DISPLAY_NAME_REQUIRED, PartyResponseCode.BLANK_DISPLAY_NAME,
                DomainViolation.BIRTH_DATE_IN_FUTURE, PartyResponseCode.BIRTH_DATE_IN_FUTURE,
                DomainViolation.DATE_OF_DEATH_IN_FUTURE, PartyResponseCode.DATE_OF_DEATH_IN_FUTURE,
                DomainViolation.DEATH_BEFORE_BIRTH, PartyResponseCode.DEATH_BEFORE_BIRTH,
                DomainViolation.INCORPORATION_DATE_IN_FUTURE, PartyResponseCode.INCORPORATION_DATE_IN_FUTURE,
                DomainViolation.DISSOLUTION_DATE_IN_FUTURE, PartyResponseCode.DISSOLUTION_DATE_IN_FUTURE,
                DomainViolation.DISSOLUTION_BEFORE_INCORPORATION, PartyResponseCode.DISSOLUTION_BEFORE_INCORPORATION);
        causes.forEach((violation, code) -> {
            assertTranslation(new ApplicationFailure.InvalidBusinessState(violation), code);
            assertEquals(422, code.getStatus());
        });
        assertEquals("missing-qualifying-identifier", PartyResponseCode.MISSING_QUALIFYING_IDENTIFIER.getCode());
    }

    @Test
    void leavesPersistenceAndUnexpectedFailuresForTheGlobalMapper() {
        ApplicationException persistenceFailure = new ApplicationException(
                new ApplicationFailure.PersistenceFailure());
        ApplicationException catalogFailure = new ApplicationException(
                new ApplicationFailure.IdentifierCatalogFailure());
        IllegalStateException unexpectedFailure = new IllegalStateException("internal detail");
        ApplicationException internalInvariant = new ApplicationException(
                new ApplicationFailure.InvalidBusinessState(DomainViolation.EVALUATION_DATE_REQUIRED));

        assertSame(persistenceFailure, translator.translate(persistenceFailure));
        assertSame(catalogFailure, translator.translate(catalogFailure));
        assertSame(unexpectedFailure, translator.translate(unexpectedFailure));
        assertSame(internalInvariant, translator.translate(internalInvariant));
    }

    private void assertTranslation(ApplicationFailure failure, PartyResponseCode expectedCode) {
        ApplicationException source = new ApplicationException(failure);

        ApiResponseException translated = assertInstanceOf(ApiResponseException.class,
                translator.translate(source));

        assertSame(expectedCode, translated.getResponseCode());
        assertSame(source, translated.getCause());
    }
}
