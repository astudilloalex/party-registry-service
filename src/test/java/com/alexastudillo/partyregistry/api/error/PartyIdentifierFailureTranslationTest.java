package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Pins identifier-owned HTTP codes while preserving neutral failures and unknown-error sanitization boundaries. */
class PartyIdentifierFailureTranslationTest {

    private final PartyApiErrorTranslator translator = new PartyApiErrorTranslator();

    @Test
    void translatesEveryIdentifierFailureWithoutUsingPartyVersions() {
        Map<ApplicationFailure, String> codes = Map.of(
                new ApplicationFailure.IdentifierNotFound(), "identifier-not-found",
                new ApplicationFailure.IdentifierVersionMismatch(new PartyIdentifierVersion(3), new PartyIdentifierVersion(4)), "expected-version-mismatch",
                new ApplicationFailure.InvalidIdentifierLifecycle(), "invalid-identifier-lifecycle",
                new ApplicationFailure.PrimaryIdentifierConflict(), "primary-identifier-conflict",
                new ApplicationFailure.IdentifierVersionExhausted(), "identifier-version-exhausted",
                new ApplicationFailure.InvalidIdentifierCursor(), "bad-request");
        codes.forEach((failure, code) -> {
            var original = new ApplicationException(failure);
            var translated = assertInstanceOf(ApiResponseException.class, translator.translate(original));
            assertEquals(code, translated.getResponseCode().getCode());
            assertSame(original, translated.getCause());
        });
    }

    @Test
    void classifiesKnownIdentifierDomainFailuresWithoutTransportCoupling() {
        Map<DomainViolation, String> codes = Map.of(
                DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, "invalid-identifier-lifecycle",
                DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, "identifier-version-exhausted",
                DomainViolation.PRIMARY_IDENTIFIER_CONFLICT, "primary-identifier-conflict",
                DomainViolation.IDENTIFIER_EXPIRED, "identifier-validation-failure",
                DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER, "identifier-validation-failure");
        codes.forEach((violation, code) -> {
            var domainFailure = new DomainValidationException(violation, "private-detail");
            var neutral = ApplicationException.forIdentifier(domainFailure);
            var translated = assertInstanceOf(ApiResponseException.class, translator.translate(neutral));
            assertEquals(code, translated.getResponseCode().getCode());
            assertSame(domainFailure, neutral.getCause());
        });
    }

    @Test
    void leavesUnknownIdentifierInvariantsAndTechnicalFailuresUnchanged() {
        var domainFailure = new DomainValidationException(DomainViolation.PROTECTED_IDENTIFIER_VALUE_REQUIRED, "private-ciphertext");
        var neutral = ApplicationException.forIdentifier(domainFailure);
        var technical = new IllegalStateException("private-storage-detail");
        assertSame(neutral, translator.translate(neutral));
        assertSame(technical, translator.translate(technical));
    }
}
