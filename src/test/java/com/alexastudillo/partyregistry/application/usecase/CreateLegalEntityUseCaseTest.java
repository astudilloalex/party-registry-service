package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.TODAY;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.identifier;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.scheme;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legal-entity registration with an independent required initial
 * identifier.
 */
class CreateLegalEntityUseCaseTest {

    @Test
    void registersOnlyLegalEntityDetailsInTheRequiredOrder() {
        RegistrationFixture fixture = new RegistrationFixture();

        PartyRegistrationResult result = awaitItem(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

        assertEquals(List.of(
                "fingerprint",
                "findCompleted",
                "hasCompletedKey",
                "country",
                "scheme",
                "protect",
                "registerLegalEntity"), fixture.order);
        LegalEntityResult legal = assertInstanceOf(LegalEntityResult.class, result.party());
        assertEquals(PartyType.LEGAL_ENTITY, legal.type());
        assertEquals("Analytical Engines Limited", legal.legalName());
        assertEquals("Analytical Engines", legal.tradeName());
        assertEquals("LTD", legal.legalFormCode());
        assertEquals("GB", legal.incorporationCountryCode());
        assertEquals(PartyRecordStatus.DRAFT, legal.recordStatus());
        assertEquals(0, legal.version().value());
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION,
                result.initialIdentifier().status());
        assertEquals(PartyRegistrationOutcome.CREATED, result.outcome());
        assertEquals(1, fixture.registrationPort.legalCandidates.size());
        assertTrue(fixture.registrationPort.naturalCandidates.isEmpty());
        assertEquals(2,
                fixture.registrationPort.legalCandidates.getFirst().outboxCandidates().size());
        assertEquals(METADATA,
                fixture.registrationPort.legalCandidates.getFirst().requestMetadata());
        assertEquals(new RecordingOperationObserver.CompletedCall(
                METADATA,
                ObservedOperation.APPLICATION_LEGAL_ENTITY_REGISTRATION,
                OperationOutcome.CREATED),
                fixture.observationPort.completedCalls().getFirst());
    }

    @Test
    void acceptsMissingExpirationEvenWithLegacyRequiredMetadata() {
        RegistrationFixture fixture = new RegistrationFixture();
        fixture.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH, 4, 16, true)));
        InitialPartyIdentifierInput input = new InitialPartyIdentifierInput(
                "TEST-SCHEME", "AB123456", null, null, null, false);

        var result = awaitItem(fixture.useCase.execute(command(input)));

        assertEquals(1, fixture.protectionPort.requests.size());
        assertEquals(1, fixture.registrationPort.legalCandidates.size());
        assertNull(result.initialIdentifier().expiresOn());
    }

    @Test
    void replaysBeforeUnavailableCountrySchemeAndProtectionDependencies() {
        RegistrationFixture initial = new RegistrationFixture();
        PartyRegistrationResult stored = awaitItem(initial.useCase.execute(command(identifier("TEST-SCHEME"))));
        RegistrationFixture replay = new RegistrationFixture();
        replay.registrationPort.completedBehavior = ignored -> Uni.createFrom().item(Optional.of(stored));
        replay.countryPort.behavior = (metadata, code) -> dependencyFailure("geographic-reference");
        replay.schemeRepository.behavior = code -> dependencyFailure("identifier-scheme-catalog");
        replay.protectionPort.behavior = ignored -> {
            throw new ApplicationException(
                    new ApplicationFailure.DependencyUnavailable("identifier-protection"));
        };

        PartyRegistrationResult result = awaitItem(replay.useCase.execute(command(identifier("TEST-SCHEME"))));

        assertEquals(PartyRegistrationOutcome.REPLAYED, result.outcome());
        assertEquals(stored.party(), result.party());
        assertEquals(stored.initialIdentifier(), result.initialIdentifier());
        assertEquals(List.of("fingerprint", "findCompleted"), replay.order);
        assertNoPersistence(replay);
    }

    @Test
    void rejectsLegacyAndChangedCurrentOperationKeysBeforeMutableDependencies() {
        RegistrationFixture legacy = new RegistrationFixture();
        legacy.registrationPort.legacyBehavior = ignored -> Uni.createFrom().item(true);

        assertInstanceOf(
                ApplicationFailure.IdempotencyKeyConflict.class,
                awaitFailure(legacy.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertEquals(RegisterNaturalPersonCommand.LEGACY_OPERATION,
                legacy.registrationPort.legacyCalls.getFirst().operation());
        assertEquals(List.of("fingerprint", "findCompleted", "hasCompletedKey"), legacy.order);
        assertNoPersistence(legacy);

        RegistrationFixture changed = new RegistrationFixture();
        changed.registrationPort.completedBehavior = call -> Uni.createFrom().failure(
                new ApplicationException(
                        new ApplicationFailure.IdempotencyKeyConflict(call.idempotencyKey())));
        assertInstanceOf(
                ApplicationFailure.IdempotencyKeyConflict.class,
                awaitFailure(changed.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertEquals(List.of("fingerprint", "findCompleted"), changed.order);
        assertNoPersistence(changed);
    }

    @Test
    void validatesLegalDomainAndIncorporationCountryBeforeSchemeLookup() {
        RegistrationFixture invalidDomain = new RegistrationFixture();
        RegisterLegalEntityCommand future = new RegisterLegalEntityCommand(
                METADATA,
                "legal-key",
                null,
                "Analytical Engines Limited",
                null,
                null,
                "GB",
                TODAY.plusDays(1),
                null,
                identifier("TEST-SCHEME"));
        ApplicationFailure.InvalidBusinessState domainFailure = assertInstanceOf(
                ApplicationFailure.InvalidBusinessState.class,
                awaitFailure(invalidDomain.useCase.execute(future)));
        assertEquals(DomainViolation.INCORPORATION_DATE_IN_FUTURE, domainFailure.violation());
        assertEquals(List.of("fingerprint", "findCompleted", "hasCompletedKey"), invalidDomain.order);
        assertNoPersistence(invalidDomain);

        RegistrationFixture unrecognized = new RegistrationFixture();
        unrecognized.countryPort.behavior = (metadata, code) -> Uni.createFrom().item(false);
        ApplicationFailure.UnrecognizedIncorporationCountry countryFailure = assertInstanceOf(
                ApplicationFailure.UnrecognizedIncorporationCountry.class,
                awaitFailure(unrecognized.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertEquals("GB", countryFailure.incorporationCountryCode());
        assertEquals(List.of("fingerprint", "findCompleted", "hasCompletedKey", "country"),
                unrecognized.order);
        assertNoPersistence(unrecognized);
    }

    @Test
    void propagatesIncorporationCountryDependencyFailureWithoutPersistence() {
        RegistrationFixture fixture = new RegistrationFixture();
        fixture.countryPort.behavior = (metadata, code) -> dependencyFailure("geographic-reference");

        ApplicationFailure failure = awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, failure);
        assertNoPersistence(fixture);
    }

    @Test
    void rejectsATypeIncompatibleSchemeAndSemanticIdentifierBeforeProtection() {
        RegistrationFixture incompatible = new RegistrationFixture();
        incompatible.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.NATURAL_PERSON)));

        ApplicationFailure.IncompatibleIdentifierScheme schemeFailure = assertInstanceOf(
                ApplicationFailure.IncompatibleIdentifierScheme.class,
                awaitFailure(incompatible.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertEquals(PartyType.LEGAL_ENTITY, schemeFailure.partyType());
        assertTrue(incompatible.protectionPort.requests.isEmpty());
        assertNoPersistence(incompatible);

        RegistrationFixture semantic = new RegistrationFixture();
        InitialPartyIdentifierInput invalid = new InitialPartyIdentifierInput(
                "TEST-SCHEME", "LE-1234", null, null, null, false);
        ApplicationFailure.IdentifierValidationFailure identifierFailure = assertInstanceOf(
                ApplicationFailure.IdentifierValidationFailure.class,
                awaitFailure(semantic.useCase.execute(command(invalid))));
        assertEquals(DomainViolation.IDENTIFIER_VALUE_INVALID, identifierFailure.violation());
        assertTrue(semantic.protectionPort.requests.isEmpty());
        assertNoPersistence(semantic);
    }

    @Test
    void propagatesProtectionAndDuplicateConflictsWithoutPartialRegistration() {
        RegistrationFixture protection = new RegistrationFixture();
        protection.protectionPort.behavior = ignored -> {
            throw new ApplicationException(
                    new ApplicationFailure.DependencyUnavailable("identifier-protection"));
        };
        assertInstanceOf(
                ApplicationFailure.DependencyUnavailable.class,
                awaitFailure(protection.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertNoPersistence(protection);

        RegistrationFixture duplicate = new RegistrationFixture();
        duplicate.registrationPort.legalBehavior = candidate -> Uni.createFrom().failure(
                new ApplicationException(new ApplicationFailure.IdentifierUniquenessConflict(
                        candidate.identifierScheme().id())));
        assertInstanceOf(
                ApplicationFailure.IdentifierUniquenessConflict.class,
                awaitFailure(duplicate.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertEquals(1, duplicate.registrationPort.legalCandidates.size());
        assertTrue(duplicate.registrationPort.naturalCandidates.isEmpty());
    }

    private static RegisterLegalEntityCommand command(InitialPartyIdentifierInput identifierInput) {
        return new RegisterLegalEntityCommand(
                METADATA,
                "legal-key",
                null,
                "Analytical Engines Limited",
                "Analytical Engines",
                "LTD",
                "GB",
                LocalDate.of(1843, Month.JANUARY, 1),
                null,
                identifierInput);
    }

    private static void assertNoPersistence(RegistrationFixture fixture) {
        assertTrue(fixture.registrationPort.naturalCandidates.isEmpty());
        assertTrue(fixture.registrationPort.legalCandidates.isEmpty());
    }

    private static <T> Uni<T> dependencyFailure(String dependencyName) {
        return Uni.createFrom().failure(new ApplicationException(
                new ApplicationFailure.DependencyUnavailable(dependencyName)));
    }

    /**
     * Bundles recording doubles for one isolated legal-entity registration test.
     */
    private static final class RegistrationFixture {

        final List<String> order = new ArrayList<>();
        final RegistrationUseCaseTestSupport.FingerprintDouble fingerprintPort = new RegistrationUseCaseTestSupport.FingerprintDouble(
                order);
        final RegistrationUseCaseTestSupport.PartyRegistrationPortDouble registrationPort = new RegistrationUseCaseTestSupport.PartyRegistrationPortDouble(
                order);
        final RegistrationUseCaseTestSupport.CountryDouble countryPort = new RegistrationUseCaseTestSupport.CountryDouble(
                order);
        final RegistrationUseCaseTestSupport.SchemeDouble schemeRepository = new RegistrationUseCaseTestSupport.SchemeDouble(
                order);
        final RegistrationUseCaseTestSupport.ProtectionDouble protectionPort = new RegistrationUseCaseTestSupport.ProtectionDouble(
                order);
        final RecordingOperationObserver observationPort = new RecordingOperationObserver();
        final CreateLegalEntityUseCase useCase = new CreateLegalEntityUseCase(
                fingerprintPort,
                registrationPort,
                countryPort,
                new PartyIdentifierPreparation(
                        schemeRepository,
                        new IdentifierRuleCatalog(),
                        new IdentifierSchemePolicy(),
                        protectionPort),
                RegistrationUseCaseTestSupport.CLOCK,
                observationPort);
    }
}
