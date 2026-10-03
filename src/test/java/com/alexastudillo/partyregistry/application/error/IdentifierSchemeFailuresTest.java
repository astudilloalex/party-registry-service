package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CancellationException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies deliberate catalog failure classification without reclassifying programming errors or cancellation. */
class IdentifierSchemeFailuresTest {
    @ParameterizedTest
    @MethodSource("knownViolations")
    void classifiesKnownAdministrationViolationsWithTheirOriginalCause(DomainViolation violation, ApplicationFailure expected) {
        var source = new DomainValidationException(violation, "Internal validation context");
        var translated = assertInstanceOf(ApplicationException.class, IdentifierSchemeFailures.translate(source));
        assertEquals(expected, translated.failure());
        assertSame(source, translated.getCause());
    }

    @Test
    void preservesUnclassifiedDomainAndNonDomainFailures() {
        for (Throwable failure : new Throwable[] {
                new DomainValidationException(DomainViolation.AUDIT_TIMESTAMP_ORDER, "Invalid operation clock"),
                new DomainValidationException(DomainViolation.IDENTIFIER_VALUE_INVALID, "Registration failure"),
                new IllegalStateException("Unknown persistence error"), new CancellationException(),
                new ApplicationException(new ApplicationFailure.DependencyUnavailable("catalog")) }) {
            assertSame(failure, IdentifierSchemeFailures.translate(failure));
        }
    }

    @Test
    void representsReadUniquenessAndConcurrencyFailuresIndependently() {
        assertInstanceOf(ApplicationFailure.IdentifierSchemeNotFound.class,
                new ApplicationException(new ApplicationFailure.IdentifierSchemeNotFound()).failure());
        assertInstanceOf(ApplicationFailure.IdentifierSchemeCodeConflict.class,
                new ApplicationException(new ApplicationFailure.IdentifierSchemeCodeConflict()).failure());
        assertInstanceOf(ApplicationFailure.IdentifierSchemeVersionMismatch.class,
                new ApplicationException(new ApplicationFailure.IdentifierSchemeVersionMismatch()).failure());
        assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeCursor.class,
                new ApplicationException(new ApplicationFailure.InvalidIdentifierSchemeCursor()).failure());
    }

    @Test
    void exhaustiveObservationClassificationPreservesEveryCatalogFailure() {
        var metadata = new RequestMetadata(new TenantId(UUID.randomUUID()), "operator", UUID.randomUUID());
        var classifications = Map.ofEntries(
                Map.entry(new ApplicationFailure.IdentifierSchemeNotFound(), OperationOutcome.NOT_FOUND),
                Map.entry(new ApplicationFailure.IdentifierSchemeCodeConflict(), OperationOutcome.CONFLICT),
                Map.entry(new ApplicationFailure.IdentifierSchemeRulesLocked(), OperationOutcome.CONFLICT),
                Map.entry(new ApplicationFailure.IdentifierSchemeRetired(), OperationOutcome.CONFLICT),
                Map.entry(new ApplicationFailure.InvalidIdentifierSchemeLifecycle(), OperationOutcome.CONFLICT),
                Map.entry(new ApplicationFailure.IdentifierSchemeVersionExhausted(), OperationOutcome.CONFLICT),
                Map.entry(new ApplicationFailure.IdentifierSchemeVersionMismatch(), OperationOutcome.PRECONDITION_FAILED),
                Map.entry(new ApplicationFailure.IdentifierSchemeLengthRangeInvalid(), OperationOutcome.VALIDATION_FAILED),
                Map.entry(new ApplicationFailure.InvalidIdentifierSchemeConfiguration(), OperationOutcome.VALIDATION_FAILED),
                Map.entry(new ApplicationFailure.InvalidIdentifierSchemeCursor(), OperationOutcome.VALIDATION_FAILED));
        var timeout = Duration.ofSeconds(2);
        classifications.forEach((failure, expected) -> {
            var exception = new ApplicationException(failure);
            var observed = new ArrayList<OperationOutcome>();
            var operation = OperationObservation.observe(metadata, ObservedOperation.APPLICATION_PARTY_PATCH,
                    (_, _) -> observed::add, () -> Uni.createFrom().failure(exception), _ -> OperationOutcome.APPLIED);
            var execution = operation.await();
            assertSame(exception, assertThrows(ApplicationException.class, () -> execution.atMost(timeout)));
            assertEquals(java.util.List.of(expected), observed);
        });
    }

    static Stream<Arguments> knownViolations() {
        return Stream.of(
                Arguments.of(DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID, new ApplicationFailure.IdentifierSchemeLengthRangeInvalid()),
                Arguments.of(DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID, new ApplicationFailure.IdentifierSchemeLengthRangeInvalid()),
                Arguments.of(DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID, new ApplicationFailure.IdentifierSchemeLengthRangeInvalid()),
                Arguments.of(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID, new ApplicationFailure.InvalidIdentifierSchemeConfiguration()),
                Arguments.of(DomainViolation.IDENTIFIER_SCHEME_RULES_LOCKED, new ApplicationFailure.IdentifierSchemeRulesLocked()),
                Arguments.of(DomainViolation.IDENTIFIER_SCHEME_RETIRED, new ApplicationFailure.IdentifierSchemeRetired()),
                Arguments.of(DomainViolation.IDENTIFIER_SCHEME_VERSION_OVERFLOW, new ApplicationFailure.IdentifierSchemeVersionExhausted()),
                Arguments.of(DomainViolation.IDENTIFIER_SCHEME_ACTIVATION_INVALID_STATE, new ApplicationFailure.InvalidIdentifierSchemeLifecycle()),
                Arguments.of(DomainViolation.IDENTIFIER_SCHEME_DEPRECATION_INVALID_STATE, new ApplicationFailure.InvalidIdentifierSchemeLifecycle()),
                Arguments.of(DomainViolation.IDENTIFIER_SCHEME_RETIREMENT_INVALID_STATE, new ApplicationFailure.InvalidIdentifierSchemeLifecycle()));
    }
}
