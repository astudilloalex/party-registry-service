package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.COMPLETE_VALUE;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.TENANT_ID;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.TODAY;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.identifier;
import static com.alexastudillo.partyregistry.application.usecase.RegistrationUseCaseTestSupport.scheme;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.PARTY_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant-safe registration of identifiers without changing the owning Party.
 */
class RegisterPartyIdentifierUseCaseTest {

    @Test
    void registersForANaturalPersonInExactOrderWithoutMutatingTheParty() {
        RegistrationFixture fixture = new RegistrationFixture();
        NaturalPerson original = NaturalPerson.restore(
                PARTY_ID,
                TENANT_ID,
                "Ada Lovelace",
                PartyRecordStatus.DRAFT,
                new PartyVersion(5),
                AuditInfo.initial(RegistrationUseCaseTestSupport.NOW.minusSeconds(60), "creator"),
                new NaturalPersonDetails("Ada", "Lovelace", "Ada", null, null, "GB"));
        var originalVersion = original.version();
        var originalType = original.type();
        var originalId = original.partyId();
        fixture.partyLookup.behavior = ignored -> Uni.createFrom().item(Optional.of(original.type()));
        RegisterPartyIdentifierCommand command = command(identifier("TEST-SCHEME"));

        var result = awaitItem(fixture.useCase.execute(command));

        assertEquals(List.of("partyLookup", "scheme", "protect", "registerIdentifier"), fixture.order);
        assertEquals(PARTY_ID, result.partyId());
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, result.status());
        assertEquals(0, result.version().value());
        assertEquals(PARTY_ID, fixture.registrationPort.candidates.getFirst().identifier().partyId());
        assertEquals(PartyType.NATURAL_PERSON,
                fixture.registrationPort.candidates.getFirst().partyType());
        assertEquals(1, fixture.registrationPort.candidates.getFirst().outboxCandidates().size());
        assertEquals(METADATA, fixture.registrationPort.candidates.getFirst().requestMetadata());
        assertEquals(TENANT_ID, fixture.partyLookup.calls.getFirst().tenantId());
        assertEquals(PARTY_ID, fixture.partyLookup.calls.getFirst().partyId());
        assertSame(originalId, original.partyId());
        assertSame(originalType, original.type());
        assertSame(originalVersion, original.version());
        assertEquals(new RecordingOperationObserver.CompletedCall(
                        METADATA,
                        ObservedOperation.APPLICATION_ADDITIONAL_IDENTIFIER_REGISTRATION,
                        OperationOutcome.CREATED),
                fixture.observationPort.completedCalls().getFirst());
    }

    @Test
    void registersForALegalEntityUsingItsImmutableType() {
        RegistrationFixture fixture = new RegistrationFixture();
        fixture.partyLookup.behavior = ignored -> Uni.createFrom().item(Optional.of(PartyType.LEGAL_ENTITY));
        fixture.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.LEGAL_ENTITY)));

        var result = awaitItem(fixture.useCase.execute(command(identifier("TEST-SCHEME"))));

        assertEquals(PARTY_ID, result.partyId());
        assertEquals(1, fixture.registrationPort.candidates.size());
        assertEquals(PartyType.LEGAL_ENTITY,
                fixture.registrationPort.candidates.getFirst().partyType());
        assertEquals(IdentifierSubjectType.LEGAL_ENTITY,
                fixture.registrationPort.candidates.getFirst()
                        .identifierScheme()
                        .applicableSubjectType());
    }

    @Test
    void concealsAbsentAndCrossTenantPartiesBeforeIdentifierDependencies() {
        RegistrationFixture fixture = new RegistrationFixture();
        fixture.partyLookup.behavior = ignored -> Uni.createFrom().item(Optional.empty());

        ApplicationFailure.PartyNotFound failure = assertInstanceOf(
                ApplicationFailure.PartyNotFound.class,
                awaitFailure(fixture.useCase.execute(command(identifier("TEST-SCHEME")))));

        assertEquals(PARTY_ID, failure.partyId());
        assertEquals(TENANT_ID, failure.tenantId());
        assertEquals(List.of("partyLookup"), fixture.order);
        assertNoPersistence(fixture);
    }

    @Test
    void rejectsUnknownInactiveAndTypeIncompatibleSchemes() {
        RegistrationFixture unknown = new RegistrationFixture();
        unknown.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.empty());
        assertInstanceOf(
                ApplicationFailure.UnknownIdentifierScheme.class,
                awaitFailure(unknown.useCase.execute(command(identifier("UNKNOWN")))));
        assertNoPersistence(unknown);

        RegistrationFixture inactive = new RegistrationFixture();
        inactive.schemeRepository.behavior = code -> Uni.createFrom().item(Optional.of(scheme(
                IdentifierSchemeStatus.DRAFT,
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
    void rejectsRuleLengthAndDateFailuresBeforeProtectionOrPersistence() {
        RegistrationFixture semantic = new RegistrationFixture();
        InitialPartyIdentifierInput invalid = new InitialPartyIdentifierInput(
                "TEST-SCHEME", "AB-1234", null, null, null, false);
        assertIdentifierViolation(
                semantic,
                invalid,
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
                identifier("TEST-SCHEME"),
                DomainViolation.IDENTIFIER_VALUE_TOO_SHORT);

        RegistrationFixture expired = new RegistrationFixture();
        InitialPartyIdentifierInput alreadyExpired = new InitialPartyIdentifierInput(
                "TEST-SCHEME",
                COMPLETE_VALUE,
                null,
                TODAY.minusYears(1),
                TODAY.minusDays(1),
                false);
        assertIdentifierViolation(
                expired,
                alreadyExpired,
                DomainViolation.IDENTIFIER_EXPIRED);
    }

    @Test
    void propagatesProtectionFailureAndDuplicateIdentifierConflict() {
        RegistrationFixture protection = new RegistrationFixture();
        protection.protectionPort.behavior = ignored -> {
            throw new ApplicationException(new ApplicationFailure.DependencyUnavailable("identifier-protection"));
        };
        assertInstanceOf(
                ApplicationFailure.DependencyUnavailable.class,
                awaitFailure(protection.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertNoPersistence(protection);

        RegistrationFixture duplicate = new RegistrationFixture();
        duplicate.registrationPort.behavior = candidate -> Uni.createFrom().failure(
                new ApplicationException(new ApplicationFailure.IdentifierUniquenessConflict(
                        candidate.identifierScheme().id())));
        assertInstanceOf(
                ApplicationFailure.IdentifierUniquenessConflict.class,
                awaitFailure(duplicate.useCase.execute(command(identifier("TEST-SCHEME")))));
        assertEquals(1, duplicate.registrationPort.candidates.size());
    }

    private static RegisterPartyIdentifierCommand command(InitialPartyIdentifierInput identifierInput) {
        return new RegisterPartyIdentifierCommand(
                METADATA,
                "additional-key",
                PARTY_ID,
                identifierInput);
    }

    private static void assertIdentifierViolation(
            RegistrationFixture fixture,
            InitialPartyIdentifierInput identifierInput,
            DomainViolation expectedViolation) {
        ApplicationFailure.IdentifierValidationFailure failure = assertInstanceOf(
                ApplicationFailure.IdentifierValidationFailure.class,
                awaitFailure(fixture.useCase.execute(command(identifierInput))));
        assertEquals(expectedViolation, failure.violation());
        assertTrue(fixture.protectionPort.requests.isEmpty());
        assertNoPersistence(fixture);
    }

    private static void assertNoPersistence(RegistrationFixture fixture) {
        assertTrue(fixture.registrationPort.candidates.isEmpty());
    }

    /** Bundles recording doubles for one isolated additional-identifier test. */
    private static final class RegistrationFixture {

        final List<String> order = new ArrayList<>();
        final RegistrationUseCaseTestSupport.PartyLookupDouble partyLookup =
                new RegistrationUseCaseTestSupport.PartyLookupDouble(order);
        final RegistrationUseCaseTestSupport.IdentifierRegistrationPortDouble registrationPort =
                new RegistrationUseCaseTestSupport.IdentifierRegistrationPortDouble(order);
        final RegistrationUseCaseTestSupport.SchemeDouble schemeRepository =
                new RegistrationUseCaseTestSupport.SchemeDouble(order);
        final RegistrationUseCaseTestSupport.ProtectionDouble protectionPort =
                new RegistrationUseCaseTestSupport.ProtectionDouble(order);
        final RecordingOperationObserver observationPort = new RecordingOperationObserver();
        final RegisterPartyIdentifierUseCase useCase = new RegisterPartyIdentifierUseCase(
                partyLookup,
                registrationPort,
                schemeRepository,
                new IdentifierRuleCatalog(),
                new IdentifierSchemePolicy(),
                protectionPort,
                RegistrationUseCaseTestSupport.CLOCK,
                observationPort);
    }
}
