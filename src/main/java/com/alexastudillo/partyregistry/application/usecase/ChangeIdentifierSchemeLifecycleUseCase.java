package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.error.IdentifierSchemeFailures;
import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemeActivationPolicy;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Resolves historical replay before global version checks, Domain lifecycle decisions, and atomic catalog acceptance. */
public final class ChangeIdentifierSchemeLifecycleUseCase {
    private final IdentifierSchemeMutationPort mutations;
    private final IdentifierSchemeActivationPolicy activation;
    private final Clock clock;
    private final OperationObservationPort observation;

    public ChangeIdentifierSchemeLifecycleUseCase(IdentifierSchemeMutationPort mutations, IdentifierRuleCatalog rules, Clock clock) {
        this(mutations, rules, clock, OperationObservation.noop());
    }

    /** Composes lifecycle orchestration with trusted time and subscription-scoped neutral observation. */
    public ChangeIdentifierSchemeLifecycleUseCase(IdentifierSchemeMutationPort mutations, IdentifierRuleCatalog rules, Clock clock,
            OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.activation = new IdentifierSchemeActivationPolicy(rules);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Replays original equivalent intent or accepts one eligible revision and optional completion atomically. */
    public Uni<IdentifierSchemeMutationOutcome> execute(ChangeIdentifierSchemeLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        var operation = switch (command.action()) {
            case ACTIVATE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_ACTIVATION;
            case DEPRECATE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_DEPRECATION;
            case RETIRE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_RETIREMENT;
        };
        return OperationObservation.observe(command.requestMetadata(), operation, observation,
                () -> Uni.createFrom().deferred(() -> mutations.execute(command.requestMetadata(), context -> resolve(context, command)))
                        .onFailure(DomainValidationException.class).transform(IdentifierSchemeFailures::translate),
                OperationObservation::identifierSchemeMutationOutcome);
    }

    private Uni<IdentifierSchemeMutationOutcome> resolve(IdentifierSchemeMutationContext context,
            ChangeIdentifierSchemeLifecycleCommand command) {
        if (command.idempotencyKey().isEmpty()) {
            return change(context, command);
        }
        var key = command.idempotencyKey().orElseThrow();
        var request = command.effectiveRequest();
        return context.serializeReplayKey(request.operation(), key)
                .chain(() -> context.findCompleted(request.operation(), key))
                .chain(found -> {
                    if (found.isEmpty()) {
                        return change(context, command);
                    }
                    var original = found.orElseThrow();
                    if (!request.equals(original.request())) {
                        return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(key)));
                    }
                    return Uni.createFrom().item(new IdentifierSchemeMutationOutcome(original.result(),
                            IdentifierSchemeMutationOutcome.Disposition.REPLAYED));
                });
    }

    private Uni<IdentifierSchemeMutationOutcome> change(IdentifierSchemeMutationContext context,
            ChangeIdentifierSchemeLifecycleCommand command) {
        return context.findForUpdate(command.schemeId()).map(found -> prepare(found, command))
                .chain(candidate -> context.persistScheme(candidate, command.expectedVersion()))
                .map(IdentifierSchemeResult::fromAggregate)
                .call(stored -> command.idempotencyKey().map(key -> context.recordCompletion(command.effectiveRequest().operation(), key,
                        new CompletedIdentifierSchemeOperation(command.effectiveRequest(), stored)))
                        .orElseGet(() -> Uni.createFrom().voidItem()))
                .map(stored -> new IdentifierSchemeMutationOutcome(stored, IdentifierSchemeMutationOutcome.Disposition.APPLIED));
    }

    private IdentifierScheme prepare(Optional<IdentifierScheme> found, ChangeIdentifierSchemeLifecycleCommand command) {
        var current = found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierSchemeNotFound()));
        if (!command.expectedVersion().equals(current.version())) {
            throw new ApplicationException(new ApplicationFailure.IdentifierSchemeVersionMismatch());
        }
        var occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!occurredAt.isAfter(current.auditInfo().updatedAt())) {
            occurredAt = current.auditInfo().updatedAt().plus(1, ChronoUnit.MICROS).truncatedTo(ChronoUnit.MICROS);
        }
        var user = command.requestMetadata().userId();
        return switch (command.action()) {
            case ACTIVATE -> activation.activate(current, occurredAt, user);
            case DEPRECATE -> current.deprecate(occurredAt, user);
            case RETIRE -> current.retire(occurredAt, user);
        };
    }
}
