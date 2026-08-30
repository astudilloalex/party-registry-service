package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationOutcome;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.CLOCK;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.TENANT_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.TODAY;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies natural-person creation orchestration and idempotency behavior.
 */
class CreateNaturalPersonUseCaseTest {

    @Test
    void createsWithoutCallingTheCountryPortWhenNoCountryIsSupplied() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        IdempotentCreationResult result = awaitItem(useCase.execute(command(null)));

        assertEquals(IdempotentCreationOutcome.CREATED, result.outcome());
        assertEquals(TENANT_ID, result.result().tenantId());
        assertEquals(0, result.result().version().value());
        assertEquals("Ada Lovelace", result.result().displayName());
        assertEquals(7, result.result().partyId().value().version());
        assertTrue(countryPort.codes.isEmpty());
        assertEquals(1, creationPort.preflightCalls.size());
        assertEquals(1, creationPort.calls.size());
        assertEquals("create-key", creationPort.calls.getFirst().idempotencyKey());
        assertTrue(creationPort.calls.getFirst().requestHash().matches("^[0-9a-f]{64}$"));
    }

    @Test
    void validatesARecognizedCountryBeforeCreation() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        awaitItem(useCase.execute(command("GB")));

        assertEquals(java.util.List.of("GB"), countryPort.codes);
        assertEquals(METADATA, countryPort.calls.getFirst().requestMetadata());
        assertEquals(1, creationPort.calls.size());
    }

    @Test
    void rejectsAnUnrecognizedCountryWithoutCreation() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().item(false);
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command("ZZ")));

        ApplicationFailure.UnrecognizedBirthCountry countryFailure = assertInstanceOf(
                ApplicationFailure.UnrecognizedBirthCountry.class,
                failure);
        assertEquals("ZZ", countryFailure.birthCountryCode());
        assertTrue(creationPort.calls.isEmpty());
    }

    @Test
    void propagatesDependencyUnavailableWithoutCreation() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.DependencyUnavailable("geographic-reference")));
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command("GB")));

        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, failure);
        assertTrue(creationPort.calls.isEmpty());
    }

    @Test
    void returnsTheOriginalStoredResultForAReplay() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.DependencyUnavailable("geographic-reference")));
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        NaturalPersonResult original = NaturalPersonResult.fromAggregate(
                UseCaseTestSupport.naturalPerson(
                        new com.alexastudillo.partyregistry.domain.model.PartyVersion(0),
                        null,
                        null,
                        null,
                        null));
        creationPort.preflightBehavior = call -> Uni.createFrom().item(Optional.of(
                new IdempotentCreationResult(original, IdempotentCreationOutcome.REPLAYED)));
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        IdempotentCreationResult result = awaitItem(useCase.execute(command("GB")));

        assertEquals(IdempotentCreationOutcome.REPLAYED, result.outcome());
        assertEquals(original, result.result());
        assertEquals(1, creationPort.preflightCalls.size());
        assertTrue(countryPort.calls.isEmpty());
        assertTrue(creationPort.calls.isEmpty());
    }

    @Test
    void propagatesAnIdempotencyPayloadConflict() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().item(false);
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        creationPort.preflightBehavior = call -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.IdempotencyKeyConflict(call.idempotencyKey())));
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command("ZZ")));

        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure);
        assertTrue(countryPort.calls.isEmpty());
        assertTrue(creationPort.calls.isEmpty());
    }

    @Test
    void propagatesAnIdempotencyConflictFromConcurrentCreationRecovery() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        creationPort.behavior = call -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.IdempotencyKeyConflict(call.idempotencyKey())));
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(null)));

        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure);
        assertEquals(1, creationPort.preflightCalls.size());
        assertEquals(1, creationPort.calls.size());
    }

    @Test
    void doesNotReclassifyUnexpectedPortDomainFailures() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        DomainValidationException unexpected = new DomainValidationException(
                DomainViolation.PARTY_VERSION_REQUIRED,
                "Corrupt persisted snapshot");
        creationPort.behavior = call -> Uni.createFrom().failure(unexpected);
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        Throwable failure = awaitThrowable(useCase.execute(command(null)));

        assertSame(unexpected, failure);
    }

    @Test
    void rejectsInvalidDomainStateWithoutCreation() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);
        CreateNaturalPersonCommand command = new CreateNaturalPersonCommand(
                METADATA,
                "create-key",
                null,
                "Ada",
                "Lovelace",
                null,
                TODAY.plusDays(1),
                null,
                null);

        ApplicationFailure failure = awaitFailure(useCase.execute(command));

        ApplicationFailure.InvalidBusinessState invalidState = assertInstanceOf(
                ApplicationFailure.InvalidBusinessState.class,
                failure);
        assertEquals(DomainViolation.BIRTH_DATE_IN_FUTURE, invalidState.violation());
        assertTrue(creationPort.calls.isEmpty());
    }

    @Test
    void propagatesCancellationToTheCreationPort() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        AtomicBoolean cancelled = new AtomicBoolean();
        creationPort.behavior = call -> Uni.createFrom()
                .<IdempotentCreationResult>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        UniAssertSubscriber<IdempotentCreationResult> subscriber = useCase.execute(command(null))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
    }

    @Test
    void propagatesCancellationToTheIdempotencyPreflight() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        AtomicBoolean cancelled = new AtomicBoolean();
        creationPort.preflightBehavior = call -> Uni.createFrom()
                .<Optional<IdempotentCreationResult>>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        UniAssertSubscriber<IdempotentCreationResult> subscriber = useCase.execute(command("GB"))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
        assertTrue(countryPort.calls.isEmpty());
        assertTrue(creationPort.calls.isEmpty());
    }

    @Test
    void propagatesCancellationToCountryValidation() {
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        AtomicBoolean cancelled = new AtomicBoolean();
        countryPort.behavior = (metadata, code) -> Uni.createFrom()
                .<Boolean>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        var creationPort = new UseCaseTestSupport.CreationPortDouble();
        var useCase = new CreateNaturalPersonUseCase(countryPort, creationPort, CLOCK);

        UniAssertSubscriber<IdempotentCreationResult> subscriber = useCase.execute(command("GB"))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
        assertTrue(creationPort.calls.isEmpty());
    }

    private static CreateNaturalPersonCommand command(String countryCode) {
        return new CreateNaturalPersonCommand(
                METADATA,
                "create-key",
                null,
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                countryCode);
    }
}
