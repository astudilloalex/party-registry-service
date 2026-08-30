package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonPatch;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;

import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.CLOCK;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.PARTY_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies presence-aware patch orchestration and resulting-state validation.
 */
class PatchNaturalPersonUseCaseTest {

    @Test
    void updatesPresentFieldsAndRetainsEveryOmittedField() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);
        NaturalPersonPatch patch = new NaturalPersonPatch(
                FieldUpdate.present("Augusta Ada"),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());

        NaturalPersonResult result = awaitItem(useCase.execute(command(new PartyVersion(0), patch)));

        assertEquals("Augusta Ada", result.givenNames());
        assertEquals("Lovelace", result.familyNames());
        assertEquals("Augusta Ada Lovelace", result.displayName());
        assertEquals("Ada", result.preferredName());
        assertEquals(LocalDate.of(1815, Month.DECEMBER, 10), result.birthDate());
        assertEquals(LocalDate.of(1852, Month.NOVEMBER, 27), result.dateOfDeath());
        assertEquals("GB", result.birthCountryCode());
        assertEquals(1, result.version().value());
        assertTrue(countryPort.codes.isEmpty());
        assertEquals(new PartyVersion(0), repository.updateCalls.getFirst().expectedVersion());
    }

    @Test
    void clearsExplicitNullValuesWithoutCountryValidation() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);
        NaturalPersonPatch patch = new NaturalPersonPatch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(null),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(null));

        NaturalPersonResult result = awaitItem(useCase.execute(command(new PartyVersion(0), patch)));

        assertNull(result.preferredName());
        assertNull(result.birthCountryCode());
        assertEquals(LocalDate.of(1815, Month.DECEMBER, 10), result.birthDate());
        assertEquals(LocalDate.of(1852, Month.NOVEMBER, 27), result.dateOfDeath());
        assertTrue(countryPort.codes.isEmpty());
        assertEquals(1, repository.updateCalls.size());
    }

    @Test
    void rejectsAnEmptyPatchWithoutPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                NaturalPersonPatch.empty())));

        ApplicationFailure.InvalidBusinessState invalidState = assertInstanceOf(
                ApplicationFailure.InvalidBusinessState.class,
                failure);
        assertEquals(DomainViolation.EMPTY_PATCH, invalidState.violation());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void validatesChangedDateAgainstRetainedDate() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);
        NaturalPersonPatch patch = new NaturalPersonPatch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(LocalDate.of(1860, Month.JANUARY, 1)),
                FieldUpdate.absent(),
                FieldUpdate.absent());

        ApplicationFailure failure = awaitFailure(useCase.execute(command(new PartyVersion(0), patch)));

        ApplicationFailure.InvalidBusinessState invalidState = assertInstanceOf(
                ApplicationFailure.InvalidBusinessState.class,
                failure);
        assertEquals(DomainViolation.DEATH_BEFORE_BIRTH, invalidState.violation());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void validatesAChangedNonNullCountryBeforePersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);

        NaturalPersonResult result = awaitItem(useCase.execute(command(
                new PartyVersion(0),
                countryPatch("EC"))));

        assertEquals("EC", result.birthCountryCode());
        assertEquals(java.util.List.of("EC"), countryPort.codes);
        assertEquals(METADATA, countryPort.calls.getFirst().requestMetadata());
        assertEquals(1, repository.updateCalls.size());
    }

    @Test
    void rejectsAnUnrecognizedChangedCountryWithoutPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().item(false);
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                countryPatch("ZZ"))));

        assertInstanceOf(ApplicationFailure.UnrecognizedBirthCountry.class, failure);
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void propagatesCountryDependencyFailureWithoutPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        countryPort.behavior = (metadata, code) -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.DependencyUnavailable("geographic-reference")));
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                countryPatch("EC"))));

        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, failure);
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void emitsNotFoundWithoutCountryValidationOrPersistence() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                countryPatch("EC"))));

        assertInstanceOf(ApplicationFailure.NaturalPersonNotFound.class, failure);
        assertTrue(countryPort.codes.isEmpty());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void rejectsAStaleExpectedVersionWithoutCountryValidationOrPersistence() {
        var repository = repositoryWithCurrentPerson();
        var countryPort = new UseCaseTestSupport.CountryReferenceDouble();
        var useCase = new PatchNaturalPersonUseCase(repository, countryPort, CLOCK);

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(9),
                countryPatch("EC"))));

        ApplicationFailure.ExpectedVersionMismatch mismatch = assertInstanceOf(
                ApplicationFailure.ExpectedVersionMismatch.class,
                failure);
        assertEquals(new PartyVersion(9), mismatch.expectedVersion());
        assertEquals(new PartyVersion(0), mismatch.currentVersion());
        assertTrue(countryPort.codes.isEmpty());
        assertTrue(repository.updateCalls.isEmpty());
    }

    @Test
    void propagatesAnOptimisticRaceLostDuringPersistence() {
        var repository = repositoryWithCurrentPerson();
        repository.updateBehavior = call -> Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.ExpectedVersionMismatch(
                        call.expectedVersion(),
                        call.expectedVersion().next())));
        var useCase = new PatchNaturalPersonUseCase(
                repository,
                new UseCaseTestSupport.CountryReferenceDouble(),
                CLOCK);
        NaturalPersonPatch patch = new NaturalPersonPatch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(null),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());

        ApplicationFailure failure = awaitFailure(useCase.execute(command(
                new PartyVersion(0),
                patch)));

        assertInstanceOf(ApplicationFailure.ExpectedVersionMismatch.class, failure);
        assertEquals(1, repository.updateCalls.size());
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

    private static PatchNaturalPersonCommand command(
            PartyVersion expectedVersion,
            NaturalPersonPatch patch) {
        return new PatchNaturalPersonCommand(METADATA, PARTY_ID, expectedVersion, patch);
    }

    private static NaturalPersonPatch countryPatch(String countryCode) {
        return new NaturalPersonPatch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(countryCode));
    }
}
