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
    void translatesEveryKnownApplicationFailure() {
        IdentifierSchemeId schemeId = new IdentifierSchemeId(UUID.randomUUID());
        PartyId partyId = new PartyId(PARTY_ID);
        TenantId tenantId = new TenantId(TENANT_ID);
        assertTranslation(
                new ApplicationFailure.NaturalPersonNotFound(partyId, tenantId),
                PartyResponseCode.NOT_FOUND);
        assertTranslation(
                new ApplicationFailure.IdempotencyKeyConflict("idempotency-key"),
                PartyResponseCode.CONFLICT);
        assertTranslation(
                new ApplicationFailure.ExpectedVersionMismatch(new PartyVersion(1),
                        new PartyVersion(2)),
                PartyResponseCode.PRECONDITION_FAILED);
        assertTranslation(
                new ApplicationFailure.InvalidBusinessState(DomainViolation.DEATH_BEFORE_BIRTH),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.UnrecognizedBirthCountry("ZZ"),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.UnrecognizedIncorporationCountry("ZZ"),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.DependencyUnavailable("geographic-reference"),
                PartyResponseCode.DEPENDENCY_UNAVAILABLE);
        assertTranslation(
                new ApplicationFailure.UnknownIdentifierScheme("NATIONAL_ID"),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.InactiveIdentifierScheme(schemeId),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.IncompatibleIdentifierScheme(
                        schemeId,
                        PartyType.NATURAL_PERSON),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.IdentifierValidationFailure(
                        DomainViolation.IDENTIFIER_VALUE_INVALID),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.IdentifierUniquenessConflict(schemeId),
                PartyResponseCode.CONFLICT);
        assertTranslation(
                new ApplicationFailure.PartyNotFound(partyId, tenantId),
                PartyResponseCode.NOT_FOUND);
        assertTranslation(
                new ApplicationFailure.InvalidPartyLifecycle(
                        partyId,
                        PartyRecordStatus.ACTIVE),
                PartyResponseCode.CONFLICT);
        assertTranslation(
                new ApplicationFailure.StalePartyVersion(
                        PartyVersion.initial(),
                        new PartyVersion(1)),
                PartyResponseCode.PRECONDITION_FAILED);
        assertTranslation(
                new ApplicationFailure.MissingQualifyingIdentifier(partyId),
                PartyResponseCode.UNPROCESSABLE_ENTITY);
    }

    @Test
    void leavesPersistenceAndUnexpectedFailuresForTheGlobalMapper() {
        ApplicationException persistenceFailure = new ApplicationException(
                new ApplicationFailure.PersistenceFailure());
        ApplicationException catalogFailure = new ApplicationException(
                new ApplicationFailure.IdentifierCatalogFailure());
        IllegalStateException unexpectedFailure = new IllegalStateException("internal detail");

        assertSame(persistenceFailure, translator.translate(persistenceFailure));
        assertSame(catalogFailure, translator.translate(catalogFailure));
        assertSame(unexpectedFailure, translator.translate(unexpectedFailure));
    }

    private void assertTranslation(ApplicationFailure failure, PartyResponseCode expectedCode) {
        ApplicationException source = new ApplicationException(failure);

        ApiResponseException translated = assertInstanceOf(ApiResponseException.class,
                translator.translate(source));

        assertSame(expectedCode, translated.getResponseCode());
        assertSame(source, translated.getCause());
    }
}
