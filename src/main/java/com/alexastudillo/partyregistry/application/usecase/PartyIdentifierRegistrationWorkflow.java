package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Coordinates historical creation replay and locked-scheme admission within one identifier transaction. */
final class PartyIdentifierRegistrationWorkflow {
    private final PartyIdentifierMutationPort mutations;
    private final PartyIdentifierFingerprintPort fingerprints;
    private final PartyIdentifierPreparation preparation;
    private final Clock clock;

    PartyIdentifierRegistrationWorkflow(PartyIdentifierMutationPort mutations, PartyIdentifierFingerprintPort fingerprints,
            PartyIdentifierPreparation preparation, Clock clock) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Uni<PartyIdentifierMutationOutcome> execute(RegisterPartyIdentifierCommand command) {
        return Uni.createFrom().item(() -> fingerprints.fingerprint(command.tenantId(), command.effectiveRequest()))
                .chain(fingerprint -> mutations.execute(command.requestMetadata(), context ->
                        context.serializeReplayKey(PartyIdentifierOperation.CREATE, command.idempotencyKey())
                                .chain(() -> context.findCompleted(PartyIdentifierOperation.CREATE, command.idempotencyKey()))
                                .chain(completed -> resolve(context, command, fingerprint, completed))));
    }

    private Uni<PartyIdentifierMutationOutcome> resolve(PartyIdentifierMutationContext context, RegisterPartyIdentifierCommand command,
            String fingerprint, Optional<CompletedPartyIdentifierMutation> completed) {
        if (completed.isPresent()) {
            var original = completed.orElseThrow();
            if (!fingerprints.matches(original.requestFingerprint(), fingerprint)) {
                return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(command.idempotencyKey())));
            }
            return Uni.createFrom().item(new PartyIdentifierMutationOutcome(original.result(), PartyIdentifierMutationOutcome.Disposition.REPLAYED));
        }
        return context.findOwnedParty(command.partyId()).map(found -> found.orElseThrow(() ->
                new ApplicationException(new ApplicationFailure.PartyNotFound(command.partyId(), command.tenantId()))))
                .chain(type -> resolveScheme(context, command, type, fingerprint));
    }

    private Uni<PartyIdentifierMutationOutcome> resolveScheme(PartyIdentifierMutationContext context, RegisterPartyIdentifierCommand command,
            PartyType type, String fingerprint) {
        return context.findSchemeIdByCode(command.identifier().identifierSchemeCode()).map(found -> found.orElseThrow(() -> unknownScheme(command)))
                .chain(id -> context.serializeScope(command.partyId(), id)
                        .chain(() -> context.findSchemeForRegistration(id))
                        .map(found -> requireLockedScheme(command, id, type, found))
                        .chain(scheme -> create(context, command, scheme, fingerprint)));
    }

    private IdentifierScheme requireLockedScheme(RegisterPartyIdentifierCommand command, IdentifierSchemeId resolved,
            PartyType type, Optional<IdentifierScheme> found) {
        var scheme = found.orElseThrow(() -> unknownScheme(command));
        if (!resolved.equals(scheme.id()) || !scheme.code().equals(command.identifier().identifierSchemeCode())) {
            throw new IllegalStateException("Locked identifier scheme does not match its resolved identity");
        }
        return preparation.requireEligible(scheme, type);
    }

    private Uni<PartyIdentifierMutationOutcome> create(PartyIdentifierMutationContext context, RegisterPartyIdentifierCommand command,
            IdentifierScheme scheme, String fingerprint) {
        return Uni.createFrom().item(() -> {
            Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            return preparation.createIdentifier(command.tenantId(), command.partyId(), scheme, command.identifier(),
                    LocalDate.ofInstant(occurredAt, ZoneOffset.UTC), occurredAt, command.requestMetadata().userId());
        }).chain(context::insertIdentifier)
                .call(result -> context.recordCompletion(PartyIdentifierOperation.CREATE, command.idempotencyKey(),
                        new CompletedPartyIdentifierMutation(command.tenantId(), PartyIdentifierOperation.CREATE, fingerprint, result,
                                null, command.requestMetadata().userId(), result.updatedAt())))
                .call(result -> context.appendEnabledEvent(event(command, result)))
                .map(result -> new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.APPLIED));
    }

    private static PartyIdentifierCreatedOutboxCandidate event(RegisterPartyIdentifierCommand command, PartyIdentifierResult result) {
        return new PartyIdentifierCreatedOutboxCandidate(UuidV7.generate(result.updatedAt()), command.tenantId(), result.identifierId(),
                result.version(), result.partyId(), result.schemeCode(), result.status(), result.updatedAt(),
                command.requestMetadata().processId(), command.requestMetadata().userId());
    }

    private static ApplicationException unknownScheme(RegisterPartyIdentifierCommand command) {
        return new ApplicationException(new ApplicationFailure.UnknownIdentifierScheme(command.identifier().identifierSchemeCode()));
    }
}
