package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.CreateIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies replay-first global creation precedence, exact effective intent, trusted audit, and atomic acceptance. */
class CreateIdentifierSchemeUseCaseTest {
    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "creator", UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-09-30T01:02:03.123456789Z");

    @Test
    void createsOneDraftAndCompletionWithOneMicrosecondClockCaptureAfterGlobalCodeEvaluation() {
        var port = new IdentifierSchemeMutationPortStub();
        var clock = new CountingClock();
        var input = input();
        var command = new CreateIdentifierSchemeCommand(METADATA, "key", input);
        var deferred = new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), clock).execute(command);
        assertTrue(port.calls.isEmpty());
        assertEquals(0, clock.captures);
        var outcome = deferred.await().atMost(WAIT);
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, outcome.disposition());
        assertEquals(IdentifierSchemeStatus.DRAFT, outcome.scheme().status());
        assertEquals(0, outcome.scheme().version().value());
        assertEquals(7, outcome.scheme().id().value().version());
        assertEquals(NOW.truncatedTo(ChronoUnit.MICROS), outcome.scheme().createdAt());
        assertEquals(outcome.scheme().createdAt(), outcome.scheme().updatedAt());
        assertEquals("creator", outcome.scheme().createdBy());
        assertEquals("creator", outcome.scheme().updatedBy());
        assertEquals(input.code(), outcome.scheme().code());
        assertEquals(1, clock.captures);
        assertEquals(1, port.schemes.size());
        assertEquals(1, port.completions.size());
        assertEquals(outcome.scheme(), port.completions.values().iterator().next().result());
        assertEquals(List.of("begin", "key", "completion", "code", "exists", "insert", "record", "commit", "close"), port.calls);
    }

    @Test
    void equivalentRetryReturnsOriginalDraftAuditWithoutEvaluatingLaterRetirementOrCapturingTime() {
        var port = new IdentifierSchemeMutationPortStub();
        var clock = new CountingClock();
        var useCase = new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), clock);
        var original = useCase.execute(new CreateIdentifierSchemeCommand(METADATA, "key", input())).await().atMost(WAIT);
        var retired = original.scheme().toAggregate().retire(NOW.plusSeconds(1), "withdrawer");
        port.schemes.put(retired.id(), retired);
        port.calls.clear();
        var retryMetadata = new RequestMetadata(METADATA.tenantId(), "retry-user", UUID.randomUUID());
        var replay = useCase.execute(new CreateIdentifierSchemeCommand(retryMetadata, "key", input())).await().atMost(WAIT);
        assertEquals(original.scheme(), replay.scheme());
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.REPLAYED, replay.disposition());
        assertEquals(retired, port.schemes.get(retired.id()));
        assertEquals(1, clock.captures);
        assertEquals(1, port.completions.size());
        assertEquals(List.of("begin", "key", "completion", "commit", "close"), port.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "country", "category", "subject", "name", "description", "normalizer", "validator", "minimum", "maximum", "expiration"})
    void changedEffectivePropertyConflictsBeforeAnyGlobalCodeOrConfigurationEvaluation(String property) {
        var port = new IdentifierSchemeMutationPortStub();
        var clock = new CountingClock();
        var useCase = new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), clock);
        useCase.execute(new CreateIdentifierSchemeCommand(METADATA, "key", input())).await().atMost(WAIT);
        port.calls.clear();
        var command = new CreateIdentifierSchemeCommand(METADATA, "key", changed(property));
        var execution = useCase.execute(command).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure.failure());
        assertEquals(List.of("begin", "key", "completion", "rollback", "close"), port.calls);
        assertEquals(1, clock.captures);
        assertEquals(1, port.schemes.size());
        assertEquals(1, port.completions.size());
    }

    @Test
    void anotherTenantReceivesGlobalRetiredCodeConflictBeforeInvalidConfiguration() {
        var port = new IdentifierSchemeMutationPortStub();
        var clock = new CountingClock();
        var current = IdentifierSchemePortContractTest.scheme().retire(NOW, "withdrawer");
        port.schemes.put(current.id(), current);
        var invalid = new IdentifierSchemeCreateInput(current.code(), "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Scheme", null, "OBSOLETE", "OBSOLETE", null, new BigInteger("999999999999999999999999999999"), false);
        var anotherTenant = new RequestMetadata(new TenantId(UUID.randomUUID()), "another", UUID.randomUUID());
        var useCase = new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), clock);
        var command = new CreateIdentifierSchemeCommand(anotherTenant, "key", invalid);
        var execution = useCase.execute(command).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.IdentifierSchemeCodeConflict.class, failure.failure());
        assertEquals(List.of("begin", "key", "completion", "code", "exists", "rollback", "close"), port.calls);
        assertEquals(0, clock.captures);
        assertTrue(port.completions.isEmpty());
        assertEquals(current, port.schemes.get(current.id()));
    }

    @Test
    void invalidRulesOrArbitraryIntegralBoundsSaveNoSchemeCompletionOrAcceptanceTime() {
        for (var invalid : List.of(changed("normalizer"), changed("validator"), changed("maximum"))) {
            var port = new IdentifierSchemeMutationPortStub();
            var clock = new CountingClock();
            var useCase = new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), clock);
            var command = new CreateIdentifierSchemeCommand(METADATA, "key", invalid);
            var execution = useCase.execute(command).await();
            var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
            if (invalid.maximumLength() == null) {
                assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeConfiguration.class, failure.failure());
            } else {
                assertInstanceOf(ApplicationFailure.IdentifierSchemeLengthRangeInvalid.class, failure.failure());
            }
            assertTrue(port.schemes.isEmpty());
            assertTrue(port.completions.isEmpty());
            assertEquals(0, clock.captures);
        }
    }

    @Test
    void failedCompletionLeavesItsKeyReusableAndExposesNoPartialDraft() {
        var port = new IdentifierSchemeMutationPortStub();
        var expected = new IllegalStateException("Injected recording failure");
        port.completionFailure = expected;
        var useCase = new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), new CountingClock());
        var command = new CreateIdentifierSchemeCommand(METADATA, "key", input());
        var execution = useCase.execute(command).await();
        assertSame(expected, assertThrows(IllegalStateException.class, () -> execution.atMost(WAIT)));
        assertTrue(port.schemes.isEmpty());
        assertTrue(port.completions.isEmpty());
        port.completionFailure = null;
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, useCase.execute(command).await().atMost(WAIT).disposition());
        assertEquals(1, port.schemes.size());
        assertEquals(1, port.completions.size());
    }

    private static IdentifierSchemeCreateInput input() {
        return IdentifierSchemePortContractTest.input();
    }

    private static IdentifierSchemeCreateInput changed(String property) {
        var original = input();
        return new IdentifierSchemeCreateInput(property.equals("code") ? "EXACT_CODE" : original.code(),
                property.equals("country") ? "US" : original.issuingCountryCode(),
                property.equals("category") ? IdentifierCategory.PASSPORT : original.category(),
                property.equals("subject") ? IdentifierSubjectType.NATURAL_PERSON : original.applicableSubjectType(),
                property.equals("name") ? "Changed name" : original.name(), property.equals("description") ? "Description" : null,
                property.equals("normalizer") ? "OBSOLETE_NORMALIZER" : original.normalizerKey(),
                property.equals("validator") ? "OBSOLETE_VALIDATOR" : original.validatorKey(),
                property.equals("minimum") ? BigInteger.ONE : null,
                property.equals("maximum") ? new BigInteger("999999999999999999999999999999") : null,
                property.equals("expiration"));
    }

    /** Counts accepted-time captures independently of wall-clock timing. */
    private static final class CountingClock extends Clock {
        private int captures;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(NOW, zone);
        }

        @Override
        public Instant instant() {
            captures++;
            return NOW;
        }
    }
}
