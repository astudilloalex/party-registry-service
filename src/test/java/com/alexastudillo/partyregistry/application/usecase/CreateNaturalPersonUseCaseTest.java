package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
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

import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.COMPLETE_VALUE;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.FINGERPRINT;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.NORMALIZED_VALUE;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.TODAY;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.identifier;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.scheme;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies identifier-required natural-person registration.
 */
class CreateNaturalPersonUseCaseTest {

        @Test
        void executesTheRequiredWorkflowInExactDependencyOrder() {
                RegistrationFixture fixture = new RegistrationFixture();

                PartyRegistrationResult result = awaitItem(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

                assertEquals(List.of(
                                "fingerprint",
                                "findCompleted",
                                "hasCompletedKey",
                                "country",
                                "scheme",
                                "protect",
                                "registerNaturalPerson"), fixture.order);
                assertEquals(PartyRegistrationOutcome.CREATED, result.outcome());
                assertEquals(1, fixture.registrationPort.naturalCandidates.size());
                assertEquals(METADATA,
                                fixture.registrationPort.naturalCandidates.getFirst().requestMetadata());
                assertEquals(
                                List.of(new RecordingOperationObserver.CompletedCall(
                                                METADATA,
                                                ObservedOperation.APPLICATION_NATURAL_PERSON_REGISTRATION,
                                                OperationOutcome.CREATED)),
                                fixture.observationPort.completedCalls());
        }

        @Test
        void replaysBeforeCountrySchemeAndProtectionDependencies() {
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
                assertEquals(OperationOutcome.REPLAYED,
                                replay.observationPort.completedCalls().getFirst().outcome());
        }

        @Test
        void rejectsACompletedLegacyNaturalPersonKeyBeforeMutableDependencies() {
                RegistrationFixture fixture = new RegistrationFixture();
                fixture.registrationPort.legacyBehavior = ignored -> Uni.createFrom().item(true);

                ApplicationFailure failure = awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

                assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure);
                assertEquals(RegisterNaturalPersonCommand.LEGACY_OPERATION,
                                fixture.registrationPort.legacyCalls.getFirst().operation());
                assertEquals(List.of("fingerprint", "findCompleted", "hasCompletedKey"), fixture.order);
                assertNoPersistence(fixture);
        }

        @Test
        void letsTheCurrentOperationPortRejectChangedInputByFingerprint() {
                RegistrationFixture fixture = new RegistrationFixture();
                String changedFingerprint = "c".repeat(64);
                fixture.fingerprintPort.behavior = ignored -> changedFingerprint;
                fixture.registrationPort.completedBehavior = call -> Uni.createFrom().failure(
                                new ApplicationException(
                                                new ApplicationFailure.IdempotencyKeyConflict(call.idempotencyKey())));

                ApplicationFailure failure = awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

                assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure);
                assertEquals(changedFingerprint,
                                fixture.registrationPort.completedCalls.getFirst().fingerprint());
                assertEquals(List.of("fingerprint", "findCompleted"), fixture.order);
                assertNoPersistence(fixture);
        }

        @Test
        void validatesPartyDomainStateBeforeCountryAndIdentifierDependencies() {
                RegistrationFixture fixture = new RegistrationFixture();
                RegisterNaturalPersonCommand invalid = new RegisterNaturalPersonCommand(
                                METADATA,
                                "natural-key",
                                null,
                                "Ada",
                                "Lovelace",
                                null,
                                TODAY.plusDays(1),
                                null,
                                "GB",
                                identifier("TEST-SCHEME"));

                ApplicationFailure.InvalidBusinessState failure = assertInstanceOf(
                                ApplicationFailure.InvalidBusinessState.class,
                                awaitFailure(fixture.useCase.execute(invalid)));

                assertEquals(DomainViolation.BIRTH_DATE_IN_FUTURE, failure.violation());
                assertEquals(List.of("fingerprint", "findCompleted", "hasCompletedKey"), fixture.order);
                assertNoPersistence(fixture);
        }

        @Test
        void distinguishesUnrecognizedAndUnavailableBirthCountryFailures() {
                RegistrationFixture unrecognized = new RegistrationFixture();
                unrecognized.countryPort.behavior = (metadata, code) -> Uni.createFrom().item(false);

                ApplicationFailure.UnrecognizedBirthCountry countryFailure = assertInstanceOf(
                                ApplicationFailure.UnrecognizedBirthCountry.class,
                                awaitFailure(unrecognized.useCase.execute(command(identifier("TEST-SCHEME")))));
                assertEquals("GB", countryFailure.birthCountryCode());
                assertEquals(List.of("fingerprint", "findCompleted", "hasCompletedKey", "country"),
                                unrecognized.order);
                assertNoPersistence(unrecognized);

                RegistrationFixture unavailable = new RegistrationFixture();
                unavailable.countryPort.behavior = (metadata, code) -> dependencyFailure("geographic-reference");
                assertInstanceOf(
                                ApplicationFailure.DependencyUnavailable.class,
                                awaitFailure(unavailable.useCase.execute(command(identifier("TEST-SCHEME")))));
                assertNoPersistence(unavailable);
        }

        @Test
        void rejectsUnknownInactiveAndIncompatibleSchemes() {
                RegistrationFixture unknown = new RegistrationFixture();
                unknown.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.empty());
                assertInstanceOf(
                                ApplicationFailure.UnknownIdentifierScheme.class,
                                awaitFailure(unknown.useCase.execute(command(identifier("UNKNOWN")))));
                assertNoPersistence(unknown);

                RegistrationFixture inactive = new RegistrationFixture();
                inactive.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                                IdentifierSchemeStatus.DEPRECATED,
                                IdentifierSubjectType.BOTH)));
                assertInstanceOf(
                                ApplicationFailure.InactiveIdentifierScheme.class,
                                awaitFailure(inactive.useCase.execute(command(identifier("TEST-SCHEME")))));
                assertNoPersistence(inactive);

                RegistrationFixture incompatible = new RegistrationFixture();
                incompatible.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                                IdentifierSchemeStatus.ACTIVE,
                                IdentifierSubjectType.LEGAL_ENTITY)));
                ApplicationFailure.IncompatibleIdentifierScheme failure = assertInstanceOf(
                                ApplicationFailure.IncompatibleIdentifierScheme.class,
                                awaitFailure(incompatible.useCase.execute(command(identifier("TEST-SCHEME")))));
                assertEquals(PartyType.NATURAL_PERSON, failure.partyType());
                assertNoPersistence(incompatible);
        }

        @Test
        void rejectsSemanticAndNormalizedLengthFailuresBeforeProtection() {
                RegistrationFixture semantic = new RegistrationFixture();
                InitialPartyIdentifierInput invalidValue = new InitialPartyIdentifierInput(
                                "TEST-SCHEME", "AB-1234", null, null, null, false);
                assertIdentifierViolation(
                                semantic,
                                command(invalidValue),
                                DomainViolation.IDENTIFIER_VALUE_INVALID);

                RegistrationFixture length = new RegistrationFixture();
                length.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                                IdentifierSchemeStatus.ACTIVE,
                                IdentifierSubjectType.BOTH,
                                7,
                                16,
                                false)));
                assertIdentifierViolation(
                                length,
                                command(identifier("TEST-SCHEME")),
                                DomainViolation.IDENTIFIER_VALUE_TOO_SHORT);
        }

        @Test
        void rejectsValidityDateRequiredExpirationAndExpiredValuesBeforeProtection() {
                RegistrationFixture dateOrder = new RegistrationFixture();
                InitialPartyIdentifierInput incoherent = new InitialPartyIdentifierInput(
                                "TEST-SCHEME",
                                COMPLETE_VALUE,
                                null,
                                TODAY.plusDays(2),
                                TODAY.plusDays(1),
                                false);
                assertIdentifierViolation(
                                dateOrder,
                                command(incoherent),
                                DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER);

                RegistrationFixture required = new RegistrationFixture();
                required.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                                IdentifierSchemeStatus.ACTIVE,
                                IdentifierSubjectType.BOTH,
                                4,
                                16,
                                true)));
                InitialPartyIdentifierInput missingExpiration = new InitialPartyIdentifierInput(
                                "TEST-SCHEME", COMPLETE_VALUE, null, null, null, false);
                assertIdentifierViolation(
                                required,
                                command(missingExpiration),
                                DomainViolation.IDENTIFIER_EXPIRATION_REQUIRED);

                RegistrationFixture expired = new RegistrationFixture();
                InitialPartyIdentifierInput alreadyExpired = new InitialPartyIdentifierInput(
                                "TEST-SCHEME",
                                COMPLETE_VALUE,
                                null,
                                TODAY.minusYears(2),
                                TODAY.minusDays(1),
                                false);
                assertIdentifierViolation(
                                expired,
                                command(alreadyExpired),
                                DomainViolation.IDENTIFIER_EXPIRED);
        }

        @Test
        void treatsUnsupportedActiveSchemeRulesAsCatalogFailure() {
                RegistrationFixture fixture = new RegistrationFixture();
                fixture.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                                IdentifierSchemeStatus.ACTIVE,
                                IdentifierSubjectType.BOTH,
                                4,
                                16,
                                false,
                                "UNSUPPORTED_NORMALIZER",
                                "ALPHANUMERIC_V1")));

                ApplicationFailure failure = awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

                assertInstanceOf(ApplicationFailure.IdentifierCatalogFailure.class, failure);
                assertTrue(fixture.protectionPort.requests.isEmpty());
                assertNoPersistence(fixture);
        }

        @Test
        void propagatesProtectionFailureWithoutPersistence() {
                RegistrationFixture fixture = new RegistrationFixture();
                fixture.protectionPort.behavior = ignored -> {
                        throw new ApplicationException(
                                        new ApplicationFailure.DependencyUnavailable("identifier-protection"));
                };

                ApplicationFailure failure = awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

                assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, failure);
                assertEquals("protect", fixture.order.getLast());
                assertNoPersistence(fixture);
        }

        @Test
        void propagatesDuplicateIdentifierConflictFromTheSingleAtomicCall() {
                RegistrationFixture fixture = new RegistrationFixture();
                fixture.registrationPort.naturalBehavior = candidate -> Uni.createFrom().failure(
                                new ApplicationException(new ApplicationFailure.IdentifierUniquenessConflict(
                                                candidate.identifierScheme().id())));

                ApplicationFailure failure = awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

                assertInstanceOf(ApplicationFailure.IdentifierUniquenessConflict.class, failure);
                assertEquals(1, fixture.registrationPort.naturalCandidates.size());
        }

        @Test
        void buildsIndependentSafeAggregatesAndOutboxCandidatesWithUtcEvaluation() {
                RegistrationFixture fixture = new RegistrationFixture();

                PartyRegistrationResult result = awaitItem(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));
                var candidate = fixture.registrationPort.naturalCandidates.getFirst();

                NaturalPersonResult party = assertInstanceOf(NaturalPersonResult.class, result.party());
                assertEquals(PartyType.NATURAL_PERSON, party.type());
                assertEquals(PartyRecordStatus.DRAFT, party.recordStatus());
                assertEquals(0, party.version().value());
                assertEquals(candidate.party().partyId(), candidate.initialIdentifier().partyId());
                assertEquals(candidate.party().tenantId(), candidate.initialIdentifier().tenantId());
                assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, candidate.initialIdentifier().status());
                assertEquals(0, candidate.initialIdentifier().version().value());
                assertEquals(FINGERPRINT, candidate.registrationFingerprint());
                assertEquals(NORMALIZED_VALUE,
                                fixture.protectionPort.requests.getFirst().normalizedValue());
                assertEquals(COMPLETE_VALUE,
                                fixture.protectionPort.requests.getFirst().completeValue());
                assertEquals(2, candidate.outboxCandidates().size());
                assertInstanceOf(PartyCreatedOutboxCandidate.class, candidate.outboxCandidates().get(0));
                assertInstanceOf(PartyIdentifierCreatedOutboxCandidate.class, candidate.outboxCandidates().get(1));
                assertEquals(METADATA.processId(), candidate.outboxCandidates().getFirst().correlationId());
                assertEquals(7, party.partyId().value().version());
                assertEquals(7, result.initialIdentifier().identifierId().value().version());
        }

        private static RegisterNaturalPersonCommand command(InitialPartyIdentifierInput identifierInput) {
                return new RegisterNaturalPersonCommand(
                                METADATA,
                                "natural-key",
                                null,
                                "Ada",
                                "Lovelace",
                                "Ada",
                                LocalDate.of(1815, Month.DECEMBER, 10),
                                null,
                                "GB",
                                identifierInput);
        }

        private static void assertIdentifierViolation(
                        RegistrationFixture fixture,
                        RegisterNaturalPersonCommand command,
                        DomainViolation expectedViolation) {
                ApplicationFailure.IdentifierValidationFailure failure = assertInstanceOf(
                                ApplicationFailure.IdentifierValidationFailure.class,
                                awaitFailure(fixture.useCase.execute(command)));
                assertEquals(expectedViolation, failure.violation());
                assertTrue(fixture.protectionPort.requests.isEmpty());
                assertNoPersistence(fixture);
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
         * Bundles recording doubles for one isolated natural-person registration test.
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
                final CreateNaturalPersonUseCase useCase = new CreateNaturalPersonUseCase(
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
