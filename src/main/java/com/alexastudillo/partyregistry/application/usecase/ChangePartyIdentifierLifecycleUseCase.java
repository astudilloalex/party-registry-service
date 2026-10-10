package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangePartyIdentifierLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.error.PartyIdentifierFailures;
import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.policy.PartyIdentifierPrimaryPolicy;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Coordinates replay-first verification/withdrawal and independently durable audit within one identifier transaction. */
public final class ChangePartyIdentifierLifecycleUseCase {
    private static final String UNEXPECTED_ACTION = "Unexpected identifier lifecycle action";
    private final PartyIdentifierMutationPort mutations;
    private final PartyIdentifierFingerprintPort fingerprints;
    private final Clock clock;
    private final OperationObservationPort observation;
    private final PartyIdentifierPrimaryPolicy primaries = new PartyIdentifierPrimaryPolicy();

    /** Composes owned sequential storage, exact keyed identity, one operation clock and bounded observation. */
    public ChangePartyIdentifierLifecycleUseCase(PartyIdentifierMutationPort mutations, PartyIdentifierFingerprintPort fingerprints,
            Clock clock, OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations"); this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        this.clock = Objects.requireNonNull(clock, "clock"); this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Emits only committed safe action outcomes, reproducing completed equivalent keyed results. */
    public Uni<PartyIdentifierMutationOutcome> execute(ChangePartyIdentifierLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        ObservedOperation operation = switch (command.operation()) {
            case VERIFY -> ObservedOperation.APPLICATION_PARTY_IDENTIFIER_VERIFY;
            case REJECT -> ObservedOperation.APPLICATION_PARTY_IDENTIFIER_REJECT;
            case REVOKE -> ObservedOperation.APPLICATION_PARTY_IDENTIFIER_REVOKE;
            default -> throw new IllegalArgumentException(UNEXPECTED_ACTION);
        };
        return OperationObservation.observe(command.requestMetadata(), operation, observation, () -> run(command),
                outcome -> outcome.disposition() == PartyIdentifierMutationOutcome.Disposition.REPLAYED ? OperationOutcome.REPLAYED : OperationOutcome.APPLIED);
    }

    private Uni<PartyIdentifierMutationOutcome> run(ChangePartyIdentifierLifecycleCommand command) {
        if (command.idempotencyKey() == null) { return mutations.execute(command.requestMetadata(), context -> change(context, command, null)); }
        return Uni.createFrom().item(() -> fingerprints.fingerprint(command.requestMetadata().tenantId(), command.effectiveRequest()))
                .chain(digest -> mutations.execute(command.requestMetadata(), context -> context.serializeReplayKey(command.operation(), command.idempotencyKey())
                        .chain(() -> context.findCompleted(command.operation(), command.idempotencyKey()))
                        .chain(found -> found.isPresent() ? replay(command, digest, found.orElseThrow()) : change(context, command, digest))));
    }

    private Uni<PartyIdentifierMutationOutcome> replay(ChangePartyIdentifierLifecycleCommand command, String digest, CompletedPartyIdentifierMutation original) {
        if (!fingerprints.matches(original.requestFingerprint(), digest)) { return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(command.idempotencyKey()))); }
        return Uni.createFrom().item(new PartyIdentifierMutationOutcome(original.result(), PartyIdentifierMutationOutcome.Disposition.REPLAYED));
    }

    private Uni<PartyIdentifierMutationOutcome> change(PartyIdentifierMutationContext context, ChangePartyIdentifierLifecycleCommand command, String digest) {
        return PartyIdentifierMutationScope.locked(context, command.requestMetadata(), command.partyId(), command.identifierId(), command.expectedVersion())
                .chain(current -> decide(context, command, current))
                .chain(candidate -> context.updateIdentifier(candidate, command.expectedVersion()))
                .call(result -> audit(context, command, result))
                .call(result -> complete(context, command, digest, result))
                .call(result -> context.appendEnabledEvent(event(command, result)))
                .map(result -> new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.APPLIED));
    }

    private Uni<PartyIdentifier> decide(PartyIdentifierMutationContext context, ChangePartyIdentifierLifecycleCommand command, PartyIdentifier current) {
        return Uni.createFrom().deferred(() -> {
            Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            String actor = command.requestMetadata().userId();
            return switch (command.operation()) {
                case REJECT -> Uni.createFrom().item(() -> current.reject(occurredAt, actor));
                case REVOKE -> Uni.createFrom().item(() -> current.revoke(occurredAt, actor));
                case VERIFY -> verify(context, current, occurredAt, actor);
                default -> Uni.createFrom().failure(new IllegalArgumentException(UNEXPECTED_ACTION));
            };
        }).onFailure(DomainValidationException.class).transform(PartyIdentifierFailures::translate);
    }

    private Uni<PartyIdentifier> verify(PartyIdentifierMutationContext context, PartyIdentifier current, Instant occurredAt, String actor) {
        // Admission failures precede any primary dependency; the scoped policy reuses this same original target/instant.
        PartyIdentifier verified = current.verify(occurredAt, actor);
        if (!current.isPrimary()) { return Uni.createFrom().item(verified); }
        return context.findCurrentPrimary(current.partyId(), current.identifierSchemeId())
                .map(found -> primaries.verify(current, found.orElse(null), occurredAt, actor));
    }

    private static Uni<Void> audit(PartyIdentifierMutationContext context, ChangePartyIdentifierLifecycleCommand command, PartyIdentifierResult result) {
        if (command.operation() == PartyIdentifierOperation.VERIFY) { return Uni.createFrom().voidItem(); }
        return context.appendActionAudit(new PartyIdentifierActionAudit(command.requestMetadata(), result.partyId(), result.identifierId(), result.version(),
                command.operation(), result.updatedAt(), command.reason()));
    }

    private static Uni<Void> complete(PartyIdentifierMutationContext context, ChangePartyIdentifierLifecycleCommand command, String digest, PartyIdentifierResult result) {
        if (command.idempotencyKey() == null) { return Uni.createFrom().voidItem(); }
        return context.recordCompletion(command.operation(), command.idempotencyKey(), new CompletedPartyIdentifierMutation(command.requestMetadata().tenantId(),
                command.operation(), digest, result, command.expectedVersion(), command.requestMetadata().userId(), result.updatedAt()));
    }

    private static PartyIdentifierChangedOutboxCandidate event(ChangePartyIdentifierLifecycleCommand command, PartyIdentifierResult result) {
        var kind = switch (command.operation()) {
            case VERIFY -> PartyIdentifierChangedOutboxCandidate.Kind.VERIFIED;
            case REJECT -> PartyIdentifierChangedOutboxCandidate.Kind.REJECTED;
            case REVOKE -> PartyIdentifierChangedOutboxCandidate.Kind.REVOKED;
            default -> throw new IllegalArgumentException(UNEXPECTED_ACTION);
        };
        return new PartyIdentifierChangedOutboxCandidate(UuidV7.generate(result.updatedAt()), command.requestMetadata().tenantId(), result.identifierId(),
                result.version(), result.partyId(), result.schemeCode(), result.status(), result.isPrimary(), kind, result.updatedAt(), command.requestMetadata().processId(), command.requestMetadata().userId());
    }
}
