package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.CreateIdentifierSchemeCommand;
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
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeLengthBounds;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Resolves durable creation replay before globally serialized uniqueness and pure configuration admission. */
public final class CreateIdentifierSchemeUseCase {
    private final IdentifierSchemeMutationPort mutations;
    private final IdentifierRuleCatalog rules;
    private final Clock clock;
    private final OperationObservationPort observation;

    public CreateIdentifierSchemeUseCase(IdentifierSchemeMutationPort mutations, IdentifierRuleCatalog rules, Clock clock) {
        this(mutations, rules, clock, OperationObservation.noop());
    }

    /** Composes creation with trusted time and subscription-scoped neutral observation. */
    public CreateIdentifierSchemeUseCase(IdentifierSchemeMutationPort mutations, IdentifierRuleCatalog rules, Clock clock,
            OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Creates one draft plus its original successful completion atomically, or replays exact historical intent. */
    public Uni<IdentifierSchemeMutationOutcome> execute(CreateIdentifierSchemeCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_CREATION, observation,
                () -> Uni.createFrom().deferred(() -> mutations.execute(command.requestMetadata(), context ->
                        context.serializeReplayKey(command.effectiveRequest().operation(), command.idempotencyKey())
                                .chain(() -> context.findCompleted(command.effectiveRequest().operation(), command.idempotencyKey()))
                                .chain(completed -> resolve(context, command, completed))))
                        .onFailure(DomainValidationException.class).transform(IdentifierSchemeFailures::translate),
                OperationObservation::identifierSchemeMutationOutcome);
    }

    private Uni<IdentifierSchemeMutationOutcome> resolve(IdentifierSchemeMutationContext context,
            CreateIdentifierSchemeCommand command, Optional<CompletedIdentifierSchemeOperation> completed) {
        if (completed.isPresent()) {
            var original = completed.orElseThrow();
            if (!original.request().equals(command.effectiveRequest())) {
                return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(command.idempotencyKey())));
            }
            return Uni.createFrom().item(new IdentifierSchemeMutationOutcome(original.result(), IdentifierSchemeMutationOutcome.Disposition.REPLAYED));
        }
        var input = command.effectiveRequest();
        return context.serializeCode(input.code()).chain(() -> context.codeExists(input.code()))
                .chain(exists -> Boolean.TRUE.equals(exists)
                        ? Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdentifierSchemeCodeConflict()))
                        : createNew(context, command));
    }

    private Uni<IdentifierSchemeMutationOutcome> createNew(IdentifierSchemeMutationContext context,
            CreateIdentifierSchemeCommand command) {
        return Uni.createFrom().item(() -> {
            var input = command.effectiveRequest();
            var bounds = IdentifierSchemeLengthBounds.fromIntegralInputs(input.minimumLength(), input.maximumLength());
            rules.requireSupportedKeys(input.normalizerKey(), input.validatorKey());
            var occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            return IdentifierScheme.create(new IdentifierSchemeId(UuidV7.generate(occurredAt)), input.code(), input.issuingCountryCode(),
                    input.category(), input.applicableSubjectType(), input.name(), input.description(), input.normalizerKey(),
                    input.validatorKey(), bounds.minimumLength(), bounds.maximumLength(), input.requiresExpiration(),
                    occurredAt, command.requestMetadata().userId());
        }).chain(context::insert)
                .map(IdentifierSchemeResult::fromAggregate)
                .call(stored -> context.recordCompletion(command.effectiveRequest().operation(), command.idempotencyKey(),
                        new CompletedIdentifierSchemeOperation(command.effectiveRequest(), stored)))
                .map(stored -> new IdentifierSchemeMutationOutcome(stored, IdentifierSchemeMutationOutcome.Disposition.APPLIED));
    }
}
