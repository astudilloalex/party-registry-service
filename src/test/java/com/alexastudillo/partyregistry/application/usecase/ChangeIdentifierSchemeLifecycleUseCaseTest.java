package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies global lifecycle choreography, ordered rejection, one audit advance, and atomic unkeyed acceptance. */
class ChangeIdentifierSchemeLifecycleUseCaseTest {
    static final Duration WAIT = Duration.ofSeconds(2);
    static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "operator", UUID.randomUUID());

    @ParameterizedTest
    @MethodSource("matrix")
    void allStateActionPairsDelegateToDomainWithoutChangingConfiguration(IdentifierSchemeStatus status,
            IdentifierSchemeLifecycleAction action) {
        var current = scheme(status, 4, action != IdentifierSchemeLifecycleAction.ACTIVATE, 20);
        var port = stored(current);
        var clock = new CountingClock(current.auditInfo().updatedAt().minusSeconds(10));
        var useCase = new ChangeIdentifierSchemeLifecycleUseCase(port, new IdentifierRuleCatalog(), clock);
        var command = command(current, action);
        var pipeline = useCase.execute(command);
        assertTrue(port.calls.isEmpty());
        assertEquals(0, clock.reads);
        if (!allowed(status, action)) {
            var execution = pipeline.await();
            var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
            assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeLifecycle.class, failure.failure());
            assertRejected(port, current);
            return;
        }
        var outcome = pipeline.await().atMost(WAIT);
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, outcome.disposition());
        var accepted = outcome.scheme().toAggregate();
        assertEquals(target(action), accepted.status());
        assertEquals(5, accepted.version().value());
        assertEquals(current.auditInfo().createdAt(), accepted.auditInfo().createdAt());
        assertEquals(current.auditInfo().createdBy(), accepted.auditInfo().createdBy());
        assertEquals(current.auditInfo().updatedAt().plus(1, ChronoUnit.MICROS), accepted.auditInfo().updatedAt());
        assertEquals(METADATA.userId(), accepted.auditInfo().updatedBy());
        assertEquals(current, new IdentifierScheme(accepted.id(), accepted.code(), accepted.issuingCountryCode(),
                accepted.category(), accepted.applicableSubjectType(), accepted.name(), accepted.description(),
                accepted.normalizerKey(), accepted.validatorKey(), accepted.minimumLength(), accepted.maximumLength(),
                accepted.requiresExpiration(), current.status(), current.version(), current.auditInfo()));
        assertEquals(accepted, port.schemes.get(current.id()));
        assertEquals(1, clock.reads);
        assertEquals(List.of("begin", "scheme", "write", "commit", "close"), port.calls);
        assertTrue(port.completions.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(IdentifierSchemeLifecycleAction.class)
    void absenceAndStaleVersionPrecedeStateExhaustionAndConfiguration(IdentifierSchemeLifecycleAction action) {
        var current = scheme(IdentifierSchemeStatus.RETIRED, Long.MAX_VALUE, true, 32768);
        var absent = new IdentifierSchemeMutationPortStub();
        assertFailure(absent, current, command(current, action), ApplicationFailure.IdentifierSchemeNotFound.class);
        assertTrue(absent.schemes.isEmpty());
        assertFailure(stored(current), current, new ChangeIdentifierSchemeLifecycleCommand(METADATA, current.id(),
                new IdentifierSchemeVersion(0), action, Optional.empty()), ApplicationFailure.IdentifierSchemeVersionMismatch.class);
    }

    @ParameterizedTest
    @MethodSource("matrix")
    void statePrecedesExhaustionWhichPrecedesActivationEligibility(IdentifierSchemeStatus status,
            IdentifierSchemeLifecycleAction action) {
        var current = scheme(status, Long.MAX_VALUE, true, 32768);
        assertFailure(stored(current), current, command(current, action), allowed(status, action)
                ? ApplicationFailure.IdentifierSchemeVersionExhausted.class : ApplicationFailure.InvalidIdentifierSchemeLifecycle.class);
    }

    @Test
    void activationClassifiesObsoleteKeysAndHistoricalOversizedBoundsWithoutWrite() {
        var obsolete = scheme(IdentifierSchemeStatus.DRAFT, 0, true, 20);
        assertFailure(stored(obsolete), obsolete, command(obsolete, IdentifierSchemeLifecycleAction.ACTIVATE),
                ApplicationFailure.InvalidIdentifierSchemeConfiguration.class);
        var range = scheme(IdentifierSchemeStatus.DRAFT, 0, true, 32768);
        assertFailure(stored(range), range, command(range, IdentifierSchemeLifecycleAction.ACTIVATE),
                ApplicationFailure.IdentifierSchemeLengthRangeInvalid.class);
    }

    @Test
    void laterClockIsCapturedOnceAtMicrosecondPrecision() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 0, false, 20);
        var clock = new CountingClock(current.auditInfo().updatedAt().plusSeconds(1).plusNanos(987654));
        var result = new ChangeIdentifierSchemeLifecycleUseCase(stored(current), new IdentifierRuleCatalog(), clock)
                .execute(command(current, IdentifierSchemeLifecycleAction.ACTIVATE)).await().atMost(WAIT);
        assertEquals(clock.value.truncatedTo(ChronoUnit.MICROS), result.scheme().toAggregate().auditInfo().updatedAt());
        assertEquals(1, clock.reads);
    }

    @Test
    void unkeyedRepeatedActionUsesCurrentVersionRulesWithoutReplay() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 0, false, 20);
        var port = stored(current);
        var useCase = useCase(port, current);
        var command = command(current, IdentifierSchemeLifecycleAction.ACTIVATE);
        var accepted = useCase.execute(command).await().atMost(WAIT).scheme().toAggregate();
        port.calls.clear();
        assertFailure(port, accepted, command, ApplicationFailure.IdentifierSchemeVersionMismatch.class);
        port.calls.clear();
        assertFailure(port, accepted, command(accepted, IdentifierSchemeLifecycleAction.ACTIVATE),
                ApplicationFailure.InvalidIdentifierSchemeLifecycle.class);
    }

    @Test
    void guardedWriteFailureIsPreservedAndLeavesNoAcceptedChange() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 0, false, 20);
        var port = stored(current);
        var original = new ApplicationException(new ApplicationFailure.IdentifierSchemeVersionMismatch());
        port.writeFailure = original;
        var command = command(current, IdentifierSchemeLifecycleAction.ACTIVATE);
        var execution = useCase(port, current).execute(command).await();
        assertSame(original, assertThrows(ApplicationException.class, () -> execution.atMost(WAIT)));
        assertEquals(current, port.schemes.get(current.id()));
        assertTrue(port.completions.isEmpty());
        assertEquals(List.of("begin", "scheme", "write", "rollback", "close"), port.calls);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> matrix() {
        return Arrays.stream(IdentifierSchemeStatus.values()).flatMap(status -> Arrays.stream(IdentifierSchemeLifecycleAction.values())
                .map(action -> org.junit.jupiter.params.provider.Arguments.of(status, action)));
    }

    static boolean allowed(IdentifierSchemeStatus status, IdentifierSchemeLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> status == IdentifierSchemeStatus.DRAFT;
            case DEPRECATE -> status == IdentifierSchemeStatus.ACTIVE;
            case RETIRE -> status != IdentifierSchemeStatus.RETIRED;
        };
    }

    static IdentifierSchemeStatus target(IdentifierSchemeLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> IdentifierSchemeStatus.ACTIVE;
            case DEPRECATE -> IdentifierSchemeStatus.DEPRECATED;
            case RETIRE -> IdentifierSchemeStatus.RETIRED;
        };
    }

    static ChangeIdentifierSchemeLifecycleUseCase useCase(IdentifierSchemeMutationPortStub port, IdentifierScheme current) {
        return new ChangeIdentifierSchemeLifecycleUseCase(port, new IdentifierRuleCatalog(),
                Clock.fixed(current.auditInfo().updatedAt().minusSeconds(10), ZoneOffset.UTC));
    }

    static ChangeIdentifierSchemeLifecycleCommand command(IdentifierScheme current, IdentifierSchemeLifecycleAction action) {
        return new ChangeIdentifierSchemeLifecycleCommand(METADATA, current.id(), current.version(), action, Optional.empty());
    }

    static IdentifierSchemeMutationPortStub stored(IdentifierScheme scheme) {
        var port = new IdentifierSchemeMutationPortStub();
        port.schemes.put(scheme.id(), scheme);
        return port;
    }

    static IdentifierScheme scheme(IdentifierSchemeStatus status, long version, boolean obsolete, int maximum) {
        var base = IdentifierSchemePortContractTest.scheme();
        return new IdentifierScheme(new IdentifierSchemeId(UUID.randomUUID()), base.code(), base.issuingCountryCode(),
                base.category(), base.applicableSubjectType(), base.name(), "Description",
                obsolete ? "OBSOLETE_NORMALIZER" : base.normalizerKey(), obsolete ? "OBSOLETE_VALIDATOR" : base.validatorKey(),
                10, maximum, true, status, new IdentifierSchemeVersion(version), base.auditInfo());
    }

    private static void assertFailure(IdentifierSchemeMutationPortStub port, IdentifierScheme current,
            ChangeIdentifierSchemeLifecycleCommand command, Class<? extends ApplicationFailure> expected) {
        var execution = useCase(port, current).execute(command).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(expected, failure.failure());
        assertRejected(port, current);
    }

    private static void assertRejected(IdentifierSchemeMutationPortStub port, IdentifierScheme current) {
        if (port.schemes.containsKey(current.id())) {
            assertEquals(current, port.schemes.get(current.id()));
        }
        assertTrue(port.completions.isEmpty());
        assertEquals(List.of("begin", "scheme", "rollback", "close"), port.calls);
    }

    /** Counts trusted operation-time captures without affecting Domain behavior. */
    static final class CountingClock extends Clock {
        final Instant value;
        int reads;

        CountingClock(Instant value) { this.value = value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { reads++; return value; }
    }
}
