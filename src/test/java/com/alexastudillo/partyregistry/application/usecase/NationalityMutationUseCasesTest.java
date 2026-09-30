package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks country validation timing, neutral failure propagation, date capture,
 * and mutation delegation.
 */
class NationalityMutationUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-09-24T00:00:01Z");
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());
    private static final NationalityId ID = new NationalityId(UUID.randomUUID());
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()),
            "actor", UUID.randomUUID());
    private static final NationalityResult RESULT = new NationalityResult(ID, PARTY, "EC", true,
            null, null, NOW, NOW);
    private final StubMutations mutations = new StubMutations();

    @Test
    void validatesExactlyOnceOnlyForNewCreateAndDistinguishesUnrecognizedFromUnavailable() {
        AtomicInteger lookups = new AtomicInteger();
        CountryReferencePort accepted = (metadata, code) -> {
            assertEquals("EC", code);
            lookups.incrementAndGet();
            return Uni.createFrom().item(true);
        };
        var command = create("key");
        var useCase = new CreateNationalityUseCase(mutations, accepted);
        assertEquals(NationalityMutationOutcome.Disposition.APPLIED,
                await(useCase.execute(command)).disposition());
        assertEquals(1, lookups.get());
        mutations.replay = true;
        assertEquals(NationalityMutationOutcome.Disposition.REPLAYED,
                await(useCase.execute(command)).disposition());
        assertEquals(1, lookups.get());

        mutations.replay = false;
        assertInstanceOf(ApplicationFailure.UnrecognizedNationalityCountry.class,
                failure(new CreateNationalityUseCase(mutations,
                        (metadata, code) -> Uni.createFrom().item(false)).execute(command))
                        .failure());
        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class,
                failure(new CreateNationalityUseCase(mutations,
                        (metadata, code) -> Uni.createFrom().nullItem()).execute(command))
                        .failure());
        assertInstanceOf(ApplicationFailure.DependencyUnavailable.class,
                failure(new CreateNationalityUseCase(mutations,
                        (metadata, code) -> Uni.createFrom().failure(
                                new ApplicationException(
                                        new ApplicationFailure.DependencyUnavailable(
                                                "geographic-reference"))))
                        .execute(command)).failure());
        RuntimeException unexpected = new IllegalStateException("unexpected");
        var unexpectedOperation = new CreateNationalityUseCase(mutations,
                (metadata, code) -> Uni.createFrom().failure(unexpected)).execute(command);
        assertSame(unexpected, assertThrows(RuntimeException.class, () -> await(unexpectedOperation)));
        CancellationException cancelled = new CancellationException("cancelled");
        var cancelledOperation = new CreateNationalityUseCase(mutations,
                (metadata, code) -> Uni.createFrom().failure(cancelled)).execute(command);
        assertSame(cancelled, assertThrows(CancellationException.class, () -> await(cancelledOperation)));
    }

    @Test
    void changedKeyFailsBeforeRemoteCallbackAndPatchPreservesPresence() {
        AtomicInteger calls = new AtomicInteger();
        var useCase = new CreateNationalityUseCase(mutations,
                (metadata, code) -> {
                    calls.incrementAndGet();
                    return Uni.createFrom().item(true);
                });
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class,
                failure(useCase.execute(create("reused"))).failure());
        assertEquals(0, calls.get());

        PatchNationalityCommand patch = new PatchNationalityCommand(METADATA, PARTY, ID,
                FieldUpdate.absent(), FieldUpdate.present(null));
        assertEquals(RESULT, await(new PatchNationalityUseCase(mutations).execute(patch)).nationality());
        assertSame(patch, mutations.lastPatch);
        assertEquals(false, mutations.lastPatch.validFrom().isPresent());
        assertEquals(true, mutations.lastPatch.validUntil().isPresent());
    }

    @Test
    void setPrimaryCapturesUtcDateOncePerSubscription() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.ofHours(-5));
        var useCase = new SetPrimaryNationalityUseCase(mutations, clock);
        assertEquals(RESULT, await(useCase.execute(METADATA, PARTY, ID, Optional.of("key"))).nationality());
        assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 24), mutations.lastPrimary.asOfDate());
        assertEquals(Optional.of("key"), mutations.lastPrimary.idempotencyKey());
    }

    private static CreateNationalityCommand create(String key) {
        return new CreateNationalityCommand(METADATA, PARTY, "EC", true, new NationalityPeriod(null, null),
                key);
    }

    private static <T> T await(Uni<T> operation) {
        return operation.await().atMost(Duration.ofSeconds(2));
    }

    private static ApplicationException failure(Uni<?> operation) {
        return assertThrows(ApplicationException.class, () -> await(operation));
    }

    /**
     * Simulates the transaction adapter's replay-before-callback behavior without
     * infrastructure coupling.
     */
    private static final class StubMutations implements NationalityMutationPort {
        private boolean replay;
        private PatchNationalityCommand lastPatch;
        private SetPrimaryNationalityCommand lastPrimary;

        @Override
        public Uni<NationalityMutationOutcome> create(CreateNationalityCommand command,
                Supplier<Uni<Void>> validateCountry) {
            if (command.idempotencyKey().equals("reused")) {
                return Uni.createFrom().failure(new ApplicationException(
                        new ApplicationFailure.IdempotencyKeyConflict("reused")));
            }
            if (replay) {
                return Uni.createFrom().item(new NationalityMutationOutcome(RESULT,
                        NationalityMutationOutcome.Disposition.REPLAYED));
            }
            return Uni.createFrom().deferred(validateCountry::get)
                    .replaceWith(new NationalityMutationOutcome(RESULT,
                            NationalityMutationOutcome.Disposition.APPLIED));
        }

        @Override
        public Uni<NationalityMutationOutcome> patch(PatchNationalityCommand command) {
            lastPatch = command;
            return Uni.createFrom().item(new NationalityMutationOutcome(RESULT,
                    NationalityMutationOutcome.Disposition.APPLIED));
        }

        @Override
        public Uni<NationalityMutationOutcome> setPrimary(SetPrimaryNationalityCommand command) {
            lastPrimary = command;
            return Uni.createFrom().item(new NationalityMutationOutcome(RESULT,
                    NationalityMutationOutcome.Disposition.APPLIED));
        }
    }
}
