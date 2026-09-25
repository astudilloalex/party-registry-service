package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.policy.NationalityPeriodPolicy;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Performs nationality writes after key serialization without holding the root
 * lock during country validation.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveNationalityMutationAdapter implements NationalityMutationPort {

    private static final String COMMAND = "command";

    private final NationalityMutationTransaction transactions;
    private final NationalityIdempotencyPersistence replay;
    private final Clock clock;

    @Inject
    public HibernateReactiveNationalityMutationAdapter(NationalityMutationTransaction transactions,
            NationalityIdempotencyPersistence replay, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.replay = Objects.requireNonNull(replay, "replay");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Uni<NationalityMutationOutcome> create(CreateNationalityCommand command,
            Supplier<Uni<Void>> validateCountry) {
        Objects.requireNonNull(command, COMMAND);
        Objects.requireNonNull(validateCountry, "validateCountry");
        return transactions.execute(command.requestMetadata(),
                scope -> replay.serialize(scope.session(), command.requestMetadata().tenantId(),
                        NationalityOperation.CREATE, command.idempotencyKey())
                        .chain(() -> replay.findCreate(scope.session(), command))
                        .flatMap(completed -> completed.isPresent()
                                ? Uni.createFrom().item(new NationalityMutationOutcome(completed.orElseThrow(),
                                        NationalityMutationOutcome.Disposition.REPLAYED))
                                : createNew(scope, command, validateCountry)));
    }

    @Override
    public Uni<NationalityMutationOutcome> patch(PatchNationalityCommand command) {
        Objects.requireNonNull(command, COMMAND);
        return transactions.execute(command.requestMetadata(), scope -> scope.lockRoot(command.partyId())
                .chain(() -> scope.history(command.partyId()))
                .flatMap(history -> patchLocked(scope, command, history)));
    }

    @Override
    public Uni<NationalityMutationOutcome> setPrimary(SetPrimaryNationalityCommand command) {
        Objects.requireNonNull(command, COMMAND);
        return transactions.execute(command.requestMetadata(), scope -> command.idempotencyKey()
                .map(key -> replay.serialize(scope.session(), command.requestMetadata().tenantId(),
                        NationalityOperation.SET_PRIMARY, key)
                        .chain(() -> replay.findPrimary(scope.session(), command, key))
                        .flatMap(completed -> completed.isPresent()
                                ? Uni.createFrom().item(new NationalityMutationOutcome(completed.orElseThrow(),
                                        NationalityMutationOutcome.Disposition.REPLAYED))
                                : setPrimaryNew(scope, command)))
                .orElseGet(() -> setPrimaryNew(scope, command)));
    }

    private Uni<NationalityMutationOutcome> setPrimaryNew(NationalityMutationTransaction.Scope scope,
            SetPrimaryNationalityCommand command) {
        return scope.lockRoot(command.partyId())
                .chain(() -> scope.history(command.partyId()))
                .flatMap(history -> {
                    PartyNationalityEntity target = history.stream()
                            .filter(item -> item.id().equals(command.nationalityId().value()))
                            .findFirst()
                            .orElseThrow(() -> new ApplicationException(new ApplicationFailure.NationalityNotFound()));
                    requireEffective(target.toDomain(), command);
                    List<PartyNationalityEntity> demotions = history.stream()
                            .filter(item -> !item.id().equals(target.id()) && item.primary()
                                    && item.toDomain().period().overlaps(target.toDomain().period()))
                            .toList();
                    boolean changed = !target.primary() || !demotions.isEmpty();
                    if (!changed) {
                        return recordPrimaryIfKeyed(scope, command, target.toResult());
                    }
                    for (PartyNationalityEntity former : demotions) {
                        former.changePrimary(false, nextModificationTime(former.updatedAt()),
                                command.requestMetadata().userId());
                    }
                    return scope.session().flush()
                            .call(() -> {
                                if (!target.primary()) {
                                    target.changePrimary(true, nextModificationTime(target.updatedAt()),
                                            command.requestMetadata().userId());
                                }
                                return scope.session().flush();
                            })
                            .call(() -> scope.advanceRoot(command.partyId()))
                            .flatMap(ignored -> recordPrimaryIfKeyed(scope, command, target.toResult()));
                });
    }

    private static void requireEffective(PartyNationality target, SetPrimaryNationalityCommand command) {
        try {
            NationalityPeriodPolicy.requireEffective(target, command.asOfDate());
        } catch (DomainValidationException exception) {
            if (exception.violation() == DomainViolation.NATIONALITY_NOT_EFFECTIVE) {
                throw new ApplicationException(new ApplicationFailure.NationalityNotEffective(command.nationalityId()),
                        exception);
            }
            throw exception;
        }
    }

    private Uni<NationalityMutationOutcome> recordPrimaryIfKeyed(NationalityMutationTransaction.Scope scope,
            SetPrimaryNationalityCommand command, NationalityResult result) {
        return command.idempotencyKey()
                .map(key -> replay.recordPrimary(scope.session(), command, key, result,
                        clock.instant().truncatedTo(ChronoUnit.MICROS))
                        .replaceWith(
                                new NationalityMutationOutcome(result, NationalityMutationOutcome.Disposition.APPLIED)))
                .orElseGet(() -> Uni.createFrom().item(new NationalityMutationOutcome(result,
                        NationalityMutationOutcome.Disposition.APPLIED)));
    }

    private Uni<NationalityMutationOutcome> patchLocked(NationalityMutationTransaction.Scope scope,
            PatchNationalityCommand command, List<PartyNationalityEntity> history) {
        PartyNationalityEntity entity = history.stream()
                .filter(item -> item.id().equals(command.nationalityId().value()))
                .findFirst().orElseThrow(() -> new ApplicationException(new ApplicationFailure.NationalityNotFound()));
        PartyNationality current = entity.toDomain();
        Instant now = nextModificationTime(entity.updatedAt());
        PartyNationality candidate = current.withPeriod(
                current.period().merge(command.validFrom(), command.validUntil()),
                current.auditInfo().updated(now, command.requestMetadata().userId()));
        scope.validate(candidate, history);
        entity.changePeriod(candidate.period(), now, command.requestMetadata().userId());
        return scope.session().flush().call(() -> scope.advanceRoot(command.partyId()))
                .replaceWith(new NationalityMutationOutcome(entity.toResult(),
                        NationalityMutationOutcome.Disposition.APPLIED));
    }

    private Instant nextModificationTime(Instant previous) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return now.isAfter(previous) ? now : previous.plus(1, ChronoUnit.MICROS);
    }

    private Uni<NationalityMutationOutcome> createNew(NationalityMutationTransaction.Scope scope,
            CreateNationalityCommand command, Supplier<Uni<Void>> validateCountry) {
        return scope.verifyParty(command.partyId())
                .call(() -> Uni.createFrom().deferred(validateCountry::get))
                .call(() -> scope.lockRoot(command.partyId()))
                .flatMap(ignored -> scope.history(command.partyId()))
                .flatMap(history -> {
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    PartyNationality nationality = new PartyNationality(new NationalityId(UuidV7.generate(now)),
                            command.partyId(), command.countryCode(), command.isPrimary(), command.period(),
                            AuditInfo.initial(now, command.requestMetadata().userId()));
                    scope.validate(nationality, history);
                    PartyNationalityEntity entity = new PartyNationalityEntity(nationality);
                    NationalityResult result = entity.toResult();
                    return scope.session().persist(entity).call(scope.session()::flush)
                            .call(() -> scope.advanceRoot(command.partyId()))
                            .call(() -> replay.recordCreate(scope.session(), command, result, now))
                            .replaceWith(new NationalityMutationOutcome(result,
                                    NationalityMutationOutcome.Disposition.APPLIED));
                });
    }
}
