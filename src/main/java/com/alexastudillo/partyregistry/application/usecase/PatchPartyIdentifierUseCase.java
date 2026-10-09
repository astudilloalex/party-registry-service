package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Coordinates independent current-version metadata correction in one owned identifier transaction. */
public final class PatchPartyIdentifierUseCase {
    private final PartyIdentifierMutationPort mutations;
    private final Clock clock;
    private final OperationObservationPort observation;

    /** Composes transaction ownership, trusted operation time and bounded observation. */
    public PatchPartyIdentifierUseCase(PartyIdentifierMutationPort mutations, Clock clock, OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.clock = Objects.requireNonNull(clock, "clock"); this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Returns only a complete accepted metadata outcome without acquiring replay semantics. */
    public Uni<PartyIdentifierMutationOutcome> execute(PatchPartyIdentifierCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_PARTY_IDENTIFIER_PATCH, observation,
                () -> mutations.execute(command.requestMetadata(), context -> correct(context, command)), _ -> OperationOutcome.APPLIED);
    }

    private Uni<PartyIdentifierMutationOutcome> correct(PartyIdentifierMutationContext context, PatchPartyIdentifierCommand command) {
        return context.findOwnedParty(command.partyId()).map(found -> found.orElseThrow(() ->
                new ApplicationException(new ApplicationFailure.PartyNotFound(command.partyId(), command.requestMetadata().tenantId()))))
                .chain(_ -> context.findIdentifierSchemeId(command.partyId(), command.identifierId()))
                .map(found -> found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierNotFound())))
                .chain(scheme -> context.serializeScope(command.partyId(), scheme)
                        .chain(() -> context.findForUpdate(command.partyId(), command.identifierId()))
                        .map(found -> requireCurrent(found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierNotFound())), scheme, command)))
                .chain(current -> prepare(current, command))
                .chain(candidate -> context.updateIdentifier(candidate, command.expectedVersion()))
                .call(result -> context.appendEnabledEvent(event(command, result)))
                .map(result -> new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.APPLIED));
    }

    private static PartyIdentifier requireCurrent(PartyIdentifier current, IdentifierSchemeId scheme, PatchPartyIdentifierCommand command) {
        if (!current.tenantId().equals(command.requestMetadata().tenantId()) || !current.partyId().equals(command.partyId())
                || !current.identifierId().equals(command.identifierId()) || !current.identifierSchemeId().equals(scheme)) {
            throw new IllegalStateException("Locked identifier does not match its resolved owned scope");
        }
        if (!current.version().equals(command.expectedVersion())) {
            throw new ApplicationException(new ApplicationFailure.IdentifierVersionMismatch(command.expectedVersion(), current.version()));
        }
        return current;
    }

    private Uni<PartyIdentifier> prepare(PartyIdentifier current, PatchPartyIdentifierCommand command) {
        return Uni.createFrom().item(() -> current.correctMetadata(command.issuerCode(), command.issuedOn(), command.expiresOn(),
                        clock.instant().truncatedTo(ChronoUnit.MICROS), command.requestMetadata().userId()))
                .onFailure(DomainValidationException.class).transform(PatchPartyIdentifierUseCase::translateDomain);
    }

    private static RuntimeException translateDomain(DomainValidationException failure) {
        return switch (failure.violation()) {
            case PARTY_IDENTIFIER_INVALID_LIFECYCLE -> new ApplicationException(new ApplicationFailure.InvalidIdentifierLifecycle(), failure);
            case PARTY_IDENTIFIER_VERSION_OVERFLOW -> new ApplicationException(new ApplicationFailure.IdentifierVersionExhausted(), failure);
            case IDENTIFIER_EXPIRED, IDENTIFIER_VALIDITY_DATE_ORDER, IDENTIFIER_ISSUER_CODE_INVALID, IDENTIFIER_ISSUER_CODE_TOO_LONG ->
                    new ApplicationException(new ApplicationFailure.IdentifierValidationFailure(failure.violation()), failure);
            default -> new IllegalStateException("Identifier metadata invariant failed", failure);
        };
    }

    private static PartyIdentifierChangedOutboxCandidate event(PatchPartyIdentifierCommand command, PartyIdentifierResult result) {
        return new PartyIdentifierChangedOutboxCandidate(UuidV7.generate(result.updatedAt()), command.requestMetadata().tenantId(),
                result.identifierId(), result.version(), result.partyId(), result.schemeCode(), result.status(), result.isPrimary(),
                PartyIdentifierChangedOutboxCandidate.Kind.UPDATED, result.updatedAt(), command.requestMetadata().processId(), command.requestMetadata().userId());
    }
}
