package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ReplaceNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
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
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.PARTY_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.TODAY;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies complete replacement, country validation, and version handling.
 */
class ReplaceNaturalPersonUseCaseTest {

    @Test
    void replacesAllDetailsAndClearsOmittedOptionalValues() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        NaturalPersonResult result = awaitItem(useCase.execute(command(
                new PartyVersion(0),
                "Augusta Ada",
                "King",
                null,
                null,
                null,
                null)));

        assertEquals("Augusta Ada", result.givenNames());
        assertEquals("King", result.familyNames());
        assertEquals("Augusta Ada King", result.displayName());
        assertNull(result.preferredName());
        assertNull(result.birthDate());
        assertNull(result.dateOfDeath());
        assertNull(result.birthCountryCode());
        assertEquals(1, result.version().value());
        assertEquals(UseCaseTestSupport.NOW, result.updatedAt());
        assertEquals(METADATA.userId(), result.updatedBy());
        assertTrue(countryPort.codes.isEmpty());
        assertEquals(1, repository.updateCalls.size());
        assertEquals(new PartyVersion(0), repository.updateCalls.getFirst().expectedVersion());
        assertEquals(0, repository.updateCalls.getFirst().updatedPerson().version().value());
    }

    @Test
    void validatesAChangedNonNullCountryBeforePersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        NaturalPersonResult result = awaitItem(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "EC")));

        assertEquals("EC", result.birthCountryCode());
        assertEquals(java.util.List.of("EC"), countryPort.codes);
        assertEquals(METADATA, countryPort.calls.getFirst().requestMetadata());
        assertEquals(1, repository.updateCalls.size());
    }

    @Test
    void skipsCountryValidationWhenTheCountryIsUnchanged() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        awaitItem(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB")));

        assertTrue(countryPort.codes.isEmpty());
    }

    @Test
    void rejectsAnUnrecognizedChangedCountryWithoutPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().item(false);
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                "ZZ")));

        assertInstanceOf(ApplicationFailure.UnrecognizedBirthCountry.class, failure);
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void propagatesCountryDependencyFailureWithoutPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.DependencyUnavailable("geographic-reference")));
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                "EC")));

        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, failure);
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void emitsNotFoundWithoutValidationOrPersistence() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                "EC")));

        assertInstanceOf(ApplicationFailure.NaturalPersonNotFound.class, failure);
        assertTrue(countryPort.codes.isEmpty());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void rejectsAStaleExpectedVersionWithoutValidationOrPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(7),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                "EC")));

        ApplicationFailure.ExpectedVersionMismatch mismatch = assertInstanceOf(
                ApplicationFailure.ExpectedVersionMismatch.class,
                failure);
        assertEquals(new PartyVersion(7), mismatch.expectedVersion());
        assertEquals(new PartyVersion(0), mismatch.currentVersion());
        assertTrue(countryPort.codes.isEmpty());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void rejectsInvalidResultingStateWithoutPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new ReplaceNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                TODAY.plusDays(1),
                null,
                null)));

        ApplicationFailure.InvalidBusinessState invalidState = assertInstanceOf(
                ApplicationFailure.InvalidBusinessState.class,
                failure);
        assertEquals(DomainViolation.BIRTH_DATE_IN_FUTURE, invalidState.violation());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void propagatesAnOptimisticRaceLostDuringPersistence() {
        var repository = repositoryWithCurrentPerson();
        repository.updateBehavior = call -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.ExpectedVersionMismatch(
                        call.expectedVersion(),
                        call.expectedVersion().next())));
        var useCase = new ReplaceNaturalPersonUseCase(
                repository,
                new UseCaseTestSupport.CountryReferenceDouble(),
                CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null)));

        assertInstanceOf(ApplicationFailure.ExpectedVersionMismatch.class, failure);
        assertEquals(1, repository.updateCalls.size());
    }

    @Test
    void doesNotReclassifyUnexpectedRepositoryDomainFailures() {
        var repository = repositoryWithCurrentPerson();
        DomainValidationException unexpected = new DomainValidationException(
                DomainViolation.PARTY_VERSION_REQUIRED,
                "Corrupt persisted state");
        repository.updateBehavior = call -> Uni.createFrom().failure(unexpected);
        var useCase = new ReplaceNaturalPersonUseCase(
                repository,
                new UseCaseTestSupport.CountryReferenceDouble(),
                CLOCK);

        Throwable failure = awaitThrowable(useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null)));

        assertSame(unexpected, failure);
    }

    @Test
    void propagatesCancellationToPersistence() {
        var repository = repositoryWithCurrentPerson();
        AtomicBoolean cancelled = new AtomicBoolean();
        repository.updateBehavior = call -> Uni
                .createFrom().<com.alexastudillo.partyregistry.domain.model.NaturalPerson>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        var useCase = new ReplaceNaturalPersonUseCase(
                repository,
                new UseCaseTestSupport.CountryReferenceDouble(),
                CLOCK);

        UniAssertSubscriber<NaturalPersonResult> subscriber = useCase.execute(command(
                new PartyVersion(0),
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
    }

    private static UseCaseTestSupport.RepositoryDouble repositoryWithCurrentPerson() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        repository.found = Optional.of(UseCaseTestSupport.naturalPerson(
                new PartyVersion(0),
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB"));
        return repository;
    }

    private static ReplaceNaturalPersonCommand command(
            PartyVersion expectedVersion,
            String givenNames,
            String familyNames,
            String preferredName,
            LocalDate birthDate,
            LocalDate dateOfDeath,
            String birthCountryCode) {
        return new ReplaceNaturalPersonCommand(
                METADATA,
                PARTY_ID,
                expectedVersion,
                givenNames,
                familyNames,
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode);
    }
}
