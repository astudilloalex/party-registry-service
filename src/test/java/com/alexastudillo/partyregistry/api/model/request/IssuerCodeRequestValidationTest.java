package com.alexastudillo.partyregistry.api.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the same nullable issuing-entity constraints on initial and additional identifier request models. */
class IssuerCodeRequestValidationTest {

    @Test
    void bothRequestsAcceptNullBlankAndMixedCaseEntityCodes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String issuer : List.of("", " ", "\u2003", "civil_registry", "A".repeat(64))) {
                for (Object request : requests(issuer)) assertTrue(validator.validate(request).isEmpty(), issuer);
            }
            for (Object request : requests(null)) assertTrue(validator.validate(request).isEmpty());
        }
    }

    @Test
    void invalidEntityCodesUseTheirOwnFieldCodeWithoutBecomingCountryErrors() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String issuer : List.of("_REGISTRY", "CIVIL__REGISTRY", "REGISTRY_", "REGISTRY1", "REGÍSTRY",
                    "CIVIL REGISTRY", " registry ", "REG\0ISTRY")) {
                for (Object request : requests(issuer)) {
                    var codes = validator.validate(request).stream().map(ConstraintViolation::getMessageTemplate).collect(Collectors.toSet());
                    assertEquals(Set.of("issuer-code-invalid"), codes, issuer);
                }
            }
            for (String oversized : List.of("A".repeat(65), " ".repeat(65), "😀".repeat(65))) {
                for (Object request : requests(oversized)) {
                    var codes = validator.validate(request).stream().map(ConstraintViolation::getMessageTemplate).collect(Collectors.toSet());
                    assertEquals(Set.of("issuer-code-too-long"), codes);
                }
            }
            for (Object request : requests("😀".repeat(64))) {
                var codes = validator.validate(request).stream().map(ConstraintViolation::getMessageTemplate).collect(Collectors.toSet());
                assertEquals(Set.of("issuer-code-invalid"), codes);
            }
        }
    }

    private static List<Object> requests(String issuer) {
        return List.of(new InitialPartyIdentifierCreateRequest("SCHEME", "ABCD", issuer, null, null, false),
                new PartyIdentifierCreateRequest("SCHEME", "ABCD", issuer, null, null, false));
    }
}
