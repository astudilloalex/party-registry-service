package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyId;
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
class NaturalPersonApiErrorTranslatorTest {

    private static final UUID PARTY_ID = UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
    private static final UUID TENANT_ID = UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");

    private final NaturalPersonApiErrorTranslator translator = new NaturalPersonApiErrorTranslator();

    @Test
    void exposesTheOwnedCreationResponseCode() {
        assertEquals(201, NaturalPersonResponseCode.CREATED.getStatus());
        assertEquals("successful", NaturalPersonResponseCode.CREATED.getCode());
    }

    @Test
    void translatesEveryKnownApplicationFailure() {
        assertTranslation(
                new ApplicationFailure.NaturalPersonNotFound(new PartyId(PARTY_ID),
                        new TenantId(TENANT_ID)),
                NaturalPersonResponseCode.NOT_FOUND);
        assertTranslation(
                new ApplicationFailure.IdempotencyKeyConflict("idempotency-key"),
                NaturalPersonResponseCode.IDEMPOTENCY_CONFLICT);
        assertTranslation(
                new ApplicationFailure.ExpectedVersionMismatch(new PartyVersion(1),
                        new PartyVersion(2)),
                NaturalPersonResponseCode.PRECONDITION_FAILED);
        assertTranslation(
                new ApplicationFailure.InvalidBusinessState(DomainViolation.DEATH_BEFORE_BIRTH),
                NaturalPersonResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.UnrecognizedBirthCountry("ZZ"),
                NaturalPersonResponseCode.UNPROCESSABLE_ENTITY);
        assertTranslation(
                new ApplicationFailure.DependencyUnavailable("geographic-reference"),
                NaturalPersonResponseCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void leavesPersistenceAndUnexpectedFailuresForTheGlobalMapper() {
        ApplicationException persistenceFailure = new ApplicationException(
                new ApplicationFailure.PersistenceFailure());
        IllegalStateException unexpectedFailure = new IllegalStateException("internal detail");

        assertSame(persistenceFailure, translator.translate(persistenceFailure));
        assertSame(unexpectedFailure, translator.translate(unexpectedFailure));
    }

    private void assertTranslation(ApplicationFailure failure, NaturalPersonResponseCode expectedCode) {
        ApplicationException source = new ApplicationException(failure);

        ApiResponseException translated = assertInstanceOf(ApiResponseException.class,
                translator.translate(source));

        assertSame(expectedCode, translated.getResponseCode());
        assertSame(source, translated.getCause());
    }
}
