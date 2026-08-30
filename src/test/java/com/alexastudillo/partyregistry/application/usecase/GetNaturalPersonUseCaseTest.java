package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant-scoped natural-person retrieval and concealment behavior.
 */
class GetNaturalPersonUseCaseTest {

    @Test
    void returnsTheMatchingNaturalPersonResult() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        repository.found = Optional.of(UseCaseTestSupport.naturalPerson(
                new PartyVersion(4),
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB"));
        var useCase = new GetNaturalPersonUseCase(repository);

        NaturalPersonResult result = awaitItem(useCase.execute(
                new GetNaturalPersonCommand(METADATA, PARTY_ID)));

        assertEquals(PARTY_ID, result.partyId());
        assertEquals(TENANT_ID, result.tenantId());
        assertEquals(4, result.version().value());
        assertEquals("Ada", result.preferredName());
        assertEquals("GB", result.birthCountryCode());
        assertEquals(1, repository.findCalls.size());
    }

    @Test
    void emitsTheSameNotFoundFailureForEveryConcealedLookup() {
        var repository = new UseCaseTestSupport.RepositoryDouble();
        var useCase = new GetNaturalPersonUseCase(repository);
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
        var useCase = new GetNaturalPersonUseCase(repository);

        UniAssertSubscriber<NaturalPersonResult> subscriber = useCase.execute(
                new GetNaturalPersonCommand(METADATA, PARTY_ID))
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(Duration.ofSeconds(2)).cancel();

        assertTrue(cancelled.get());
    }
}
