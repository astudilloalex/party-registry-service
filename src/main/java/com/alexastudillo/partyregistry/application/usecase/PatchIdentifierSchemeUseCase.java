package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.error.IdentifierSchemeFailures;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemeMaintenancePolicy;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Orders global absence/version checks before pure maintenance admission and one atomic catalog correction. */
public final class PatchIdentifierSchemeUseCase {
    private final IdentifierSchemeMutationPort mutations;
    private final IdentifierSchemeMaintenancePolicy maintenance;
    private final Clock clock;
    private final OperationObservationPort observation;

    public PatchIdentifierSchemeUseCase(IdentifierSchemeMutationPort mutations, IdentifierRuleCatalog rules, Clock clock) {
        this(mutations, rules, clock, OperationObservation.noop());
    }

    /** Composes maintenance with trusted time and subscription-scoped neutral observation. */
    public PatchIdentifierSchemeUseCase(IdentifierSchemeMutationPort mutations, IdentifierRuleCatalog rules, Clock clock,
            OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.maintenance = new IdentifierSchemeMaintenancePolicy(rules);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Accepts a presence-aware correction with exactly one version/audit advance and no replay record. */
    public Uni<IdentifierSchemeMutationOutcome> execute(PatchIdentifierSchemeCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_PATCH, observation,
                () -> Uni.createFrom().deferred(() -> mutations.execute(command.requestMetadata(), context ->
                context.findForUpdate(command.schemeId()).map(found -> prepare(found, command))
                        .chain(candidate -> context.persistScheme(candidate, command.expectedVersion()))
                        .map(stored -> new IdentifierSchemeMutationOutcome(IdentifierSchemeResult.fromAggregate(stored),
                                IdentifierSchemeMutationOutcome.Disposition.APPLIED))))
                        .onFailure(DomainValidationException.class).transform(IdentifierSchemeFailures::translate),
                OperationObservation::identifierSchemeMutationOutcome);
    }

    private IdentifierScheme prepare(Optional<IdentifierScheme> found, PatchIdentifierSchemeCommand command) {
        var current = found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierSchemeNotFound()));
        if (!command.expectedVersion().equals(current.version())) {
            throw new ApplicationException(new ApplicationFailure.IdentifierSchemeVersionMismatch());
        }
        var occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!occurredAt.isAfter(current.auditInfo().updatedAt())) {
            occurredAt = current.auditInfo().updatedAt().plus(1, ChronoUnit.MICROS).truncatedTo(ChronoUnit.MICROS);
        }
        return maintenance.apply(current, command.changes(), occurredAt, command.requestMetadata().userId());
    }
}
