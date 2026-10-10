package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.SetPrimaryPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.error.PartyIdentifierFailures;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierPrimaryPlan;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.policy.PartyIdentifierPrimaryPolicy;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Coordinates replay-first scoped primary selection and complete immutable replacement plans. */
public final class SetPrimaryPartyIdentifierUseCase {
    private final PartyIdentifierMutationPort mutations;
    private final PartyIdentifierFingerprintPort fingerprints;
    private final Clock clock;
    private final OperationObservationPort observation;
    private final PartyIdentifierPrimaryPolicy primaries = new PartyIdentifierPrimaryPolicy();

    /** Composes owned mutation capabilities, request identity, operation time and bounded observation. */
    public SetPrimaryPartyIdentifierUseCase(PartyIdentifierMutationPort mutations, PartyIdentifierFingerprintPort fingerprints,
            Clock clock, OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Returns only a committed safe selection outcome, replaying equivalent completed requests. */
    public Uni<PartyIdentifierMutationOutcome> execute(SetPrimaryPartyIdentifierCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_PARTY_IDENTIFIER_SET_PRIMARY, observation,
                () -> run(command), outcome -> outcome.disposition() == PartyIdentifierMutationOutcome.Disposition.REPLAYED
                        ? OperationOutcome.REPLAYED : OperationOutcome.APPLIED);
    }

    private Uni<PartyIdentifierMutationOutcome> run(SetPrimaryPartyIdentifierCommand command) {
        if (command.idempotencyKey() == null) {
            return mutations.execute(command.requestMetadata(), context -> select(context, command, null));
        }
        return Uni.createFrom().item(() -> fingerprints.fingerprint(command.requestMetadata().tenantId(), command.effectiveRequest()))
                .chain(digest -> mutations.execute(command.requestMetadata(), context -> context.serializeReplayKey(PartyIdentifierOperation.SET_PRIMARY, command.idempotencyKey())
                        .chain(() -> context.findCompleted(PartyIdentifierOperation.SET_PRIMARY, command.idempotencyKey()))
                        .chain(found -> found.isPresent() ? replay(command, digest, found.orElseThrow()) : select(context, command, digest))));
    }

    private Uni<PartyIdentifierMutationOutcome> replay(SetPrimaryPartyIdentifierCommand command, String digest, CompletedPartyIdentifierMutation original) {
        if (!fingerprints.matches(original.requestFingerprint(), digest)) {
            return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(command.idempotencyKey())));
        }
        return Uni.createFrom().item(new PartyIdentifierMutationOutcome(original.result(), PartyIdentifierMutationOutcome.Disposition.REPLAYED));
    }

    private Uni<PartyIdentifierMutationOutcome> select(PartyIdentifierMutationContext context, SetPrimaryPartyIdentifierCommand command, String digest) {
        return PartyIdentifierMutationScope.locked(context, command.requestMetadata(), command.partyId(), command.identifierId(), command.expectedVersion())
                .chain(current -> plan(context, command, current))
                .chain(plan -> persist(context, command, digest, plan));
    }

    private Uni<PartyIdentifierPrimaryPlan> plan(PartyIdentifierMutationContext context, SetPrimaryPartyIdentifierCommand command, PartyIdentifier current) {
        return Uni.createFrom().deferred(() -> {
            var occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            String actor = command.requestMetadata().userId();
            // Admission precedes the primary dependency; both Domain decisions reuse the original target and one instant.
            var eligible = current.selectPrimary(occurredAt, actor);
            return context.findCurrentPrimary(eligible.partyId(), eligible.identifierSchemeId())
                    .map(found -> primaries.planSelection(current, found.orElse(null), command.replaceCurrentPrimary(), occurredAt, actor));
        }).onFailure(DomainValidationException.class).transform(PartyIdentifierFailures::translate);
    }

    private Uni<PartyIdentifierMutationOutcome> persist(PartyIdentifierMutationContext context, SetPrimaryPartyIdentifierCommand command,
            String digest, PartyIdentifierPrimaryPlan plan) {
        return demote(context, plan.formerPrimary()).chain(former -> context.updateIdentifier(plan.candidate(), command.expectedVersion())
                .call(result -> complete(context, command, digest, result))
                .call(result -> appendEvents(context, command, result, former))
                .map(result -> new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.APPLIED)));
    }

    private static Uni<Optional<PartyIdentifierResult>> demote(PartyIdentifierMutationContext context, PartyIdentifier former) {
        if (former == null) { return Uni.createFrom().item(Optional.empty()); }
        // The validated immutable plan advances the former version exactly once, before either write occurs.
        var expected = new PartyIdentifierVersion(former.version().value() - 1);
        return context.updateIdentifier(former, expected).map(Optional::of);
    }

    private static Uni<Void> complete(PartyIdentifierMutationContext context, SetPrimaryPartyIdentifierCommand command, String digest, PartyIdentifierResult result) {
        if (command.idempotencyKey() == null) { return Uni.createFrom().voidItem(); }
        return context.recordCompletion(PartyIdentifierOperation.SET_PRIMARY, command.idempotencyKey(),
                new CompletedPartyIdentifierMutation(command.requestMetadata().tenantId(), PartyIdentifierOperation.SET_PRIMARY,
                        digest, result, command.expectedVersion(), command.requestMetadata().userId(), result.updatedAt()));
    }

    private static Uni<Void> appendEvents(PartyIdentifierMutationContext context, SetPrimaryPartyIdentifierCommand command,
            PartyIdentifierResult target, Optional<PartyIdentifierResult> former) {
        return context.appendEnabledEvent(event(command, target))
                .chain(() -> former.isPresent() ? context.appendEnabledEvent(event(command, former.orElseThrow())) : Uni.createFrom().voidItem());
    }

    private static PartyIdentifierChangedOutboxCandidate event(SetPrimaryPartyIdentifierCommand command, PartyIdentifierResult result) {
        return new PartyIdentifierChangedOutboxCandidate(UuidV7.generate(result.updatedAt()), command.requestMetadata().tenantId(),
                result.identifierId(), result.version(), result.partyId(), result.schemeCode(), result.status(), result.isPrimary(),
                PartyIdentifierChangedOutboxCandidate.Kind.PRIMARY_CHANGED, result.updatedAt(), command.requestMetadata().processId(), command.requestMetadata().userId());
    }
}
