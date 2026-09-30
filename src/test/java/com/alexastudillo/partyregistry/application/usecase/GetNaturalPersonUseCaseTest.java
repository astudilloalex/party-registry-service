package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.model.NaturalPersonDetailResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.PARTY_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.TENANT_ID;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitFailure;
import static com.alexastudillo.partyregistry.application.usecase.UseCaseTestSupport.awaitItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant-scoped natural-person retrieval and concealment behavior.
 */
class GetNaturalPersonUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-12T00:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Guayaquil"));

    @Test
    void returnsTheMatchingNaturalPersonResult() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        repository.found = Optional.of(UseCaseTestSupport.naturalPerson(
                new PartyVersion(4),
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB"));
        AtomicBoolean identifiersRead = new AtomicBoolean();
        var useCase = new GetNaturalPersonUseCase(repository, (tenantId, partyId, evaluatedOn) -> {
            assertEquals(1, repository.findCalls.size());
            assertEquals(TENANT_ID, tenantId);
            assertEquals(PARTY_ID, partyId);
            assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 12), evaluatedOn);
            identifiersRead.set(true);
            return Uni.createFrom().item(List.of());
        }, CLOCK);

        NaturalPersonDetailResult detail = awaitItem(useCase.execute(
                new GetNaturalPersonCommand(METADATA, PARTY_ID)));
        NaturalPersonResult result = detail.person();

        assertEquals(PARTY_ID, result.partyId());
        assertEquals(TENANT_ID, result.tenantId());
        assertEquals(4, result.version().value());
        assertEquals("Ada", result.preferredName());
        assertEquals("GB", result.birthCountryCode());
        assertEquals(1, repository.findCalls.size());
        assertEquals(List.of(), detail.identifiers());
        assertTrue(identifiersRead.get());
    }

    @Test
    void emitsTheSameNotFoundFailureForEveryConcealedLookup() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        var useCase = new GetNaturalPersonUseCase(repository, (_, _, _) -> {
            throw new AssertionError("Identifiers must not be queried for concealed parties");
        }, CLOCK);
        TenantId otherTenant = new TenantId(UUID.randomUUID());
        List<GetNaturalPersonCommand> concealedLookups = List.of(
                new GetNaturalPersonCommand(METADATA, PARTY_ID),
                new GetNaturalPersonCommand(METADATA, new PartyId(UUID.randomUUID())),
                new GetNaturalPersonCommand(
                        new RequestMetadata(otherTenant, METADATA.userId(), METADATA.processId()),
                        PARTY_ID));

        for (GetNaturalPersonCommand command : concealedLookups) {
            ApplicationFailure failure = awaitFailure(useCase.execute(command));
            ApplicationFailure.NaturalPersonNotFound notFound = assertInstanceOf(
                    ApplicationFailure.NaturalPersonNotFound.class,
                    failure);
            assertEquals(command.partyId(), notFound.partyId());
            assertEquals(command.tenantId(), notFound.tenantId());
        }
    }

    @Test
    void propagatesCancellationToTheRepositoryLookup() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        AtomicBoolean cancelled = new AtomicBoolean();
        repository.findBehavior = call -> Uni
                .createFrom().<Optional<com.alexastudillo.partyregistry.domain.model.NaturalPerson>>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        var useCase = new GetNaturalPersonUseCase(repository, (_, _, _) -> {
            throw new AssertionError("Identifiers must not be queried before the Party is found");
        }, CLOCK);

        UniAssertSubscriber<NaturalPersonDetailResult> subscriber = useCase.execute(
                new GetNaturalPersonCommand(METADATA, PARTY_ID))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
    }

    @Test
    void returnsAnImmutableCompleteProjectionWithoutChangingHistoricalPersonData() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        var person = UseCaseTestSupport.naturalPerson(new PartyVersion(4), "Legacy Mixed Case", null, null, "GB");
        repository.found = Optional.of(person);
        List<PartyIdentifierResult> identifiers = new ArrayList<>();
        IdentifierSchemeId schemeId = new IdentifierSchemeId(UUID.randomUUID());
        for (int index = 0; index < 76; index++) {
            identifiers.add(new PartyIdentifierResult(
                    new PartyIdentifierId(UUID.randomUUID()), PARTY_ID, schemeId, "EC_PASSPORT", "*****0001",
                    PartyIdentifierStatus.PENDING_VERIFICATION, false, null, null, null, null, null,
                    PartyIdentifierVersion.initial(), NOW.plusSeconds(index), NOW.plusSeconds(index)));
        }
        var expected = List.copyOf(identifiers);
        var useCase = new GetNaturalPersonUseCase(repository, (_, _, _) -> Uni.createFrom().item(identifiers), CLOCK);

        var result = awaitItem(useCase.execute(new GetNaturalPersonCommand(METADATA, PARTY_ID)));
        identifiers.clear();

        var resultIdentifiers = result.identifiers();
        assertEquals(expected, resultIdentifiers);
        assertEquals(NaturalPersonResult.fromAggregate(person), result.person());
        assertThrows(UnsupportedOperationException.class, resultIdentifiers::clear);
    }

    @Test
    void propagatesIdentifierReadFailuresWithoutReturningPartialPersonData() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        repository.found = Optional
                .of(UseCaseTestSupport.naturalPerson(PartyVersion.initial(), null, null, null, null));
        var failure = new ApplicationFailure.DependencyUnavailable("identifier-read");
        var useCase = new GetNaturalPersonUseCase(repository,
                (_, _, _) -> Uni.createFrom().failure(new ApplicationException(failure)), CLOCK);

        assertSame(failure, awaitFailure(useCase.execute(new GetNaturalPersonCommand(METADATA, PARTY_ID))));
    }

    @Test
    void propagatesCancellationToTheIdentifierRead() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        repository.found = Optional
                .of(UseCaseTestSupport.naturalPerson(PartyVersion.initial(), null, null, null, null));
        AtomicBoolean cancelled = new AtomicBoolean();
        var useCase = new GetNaturalPersonUseCase(repository, (_, _, _) -> Uni.createFrom()
                .<List<PartyIdentifierResult>>nothing().onCancellation().invoke(() -> cancelled.set(true)), CLOCK);

        var subscriber = useCase.execute(new GetNaturalPersonCommand(METADATA, PARTY_ID))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
    }
}
