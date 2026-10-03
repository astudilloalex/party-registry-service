package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSelector;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.query.GetIdentifierSchemeQuery;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies exact global retrieval, explicit absence, and read-only all-state results. */
class GetIdentifierSchemeUseCaseTest {
    private static final Duration WAIT = Duration.ofSeconds(2);

    @ParameterizedTest
    @EnumSource(IdentifierSchemeStatus.class)
    void bothSelectorsReturnCurrentSafeDataInEveryStateAcrossRequestTenants(IdentifierSchemeStatus status) {
        var base = IdentifierSchemePortContractTest.scheme();
        var scheme = new IdentifierScheme(base.id(), base.code(), base.issuingCountryCode(), base.category(),
                base.applicableSubjectType(), base.name(), base.description(), "OBSOLETE_NORMALIZER", "OBSOLETE_VALIDATOR",
                base.minimumLength(), base.maximumLength(), true, status, base.version(), base.auditInfo());
        var result = IdentifierSchemeResult.fromAggregate(scheme);
        var reads = new ReadStub(result);
        var useCase = new GetIdentifierSchemeUseCase(reads);
        var byId = useCase.execute(new GetIdentifierSchemeQuery(metadata(), new IdentifierSchemeSelector.ById(scheme.id())));
        assertTrue(reads.calls.isEmpty());
        assertEquals(result, byId.await().atMost(WAIT));
        assertEquals(result, useCase.execute(new GetIdentifierSchemeQuery(metadata(),
                new IdentifierSchemeSelector.ByCode(scheme.code()))).await().atMost(WAIT));
        assertEquals(List.of("id", "code:" + scheme.code()), reads.calls);
        assertEquals(scheme, result.toAggregate());
    }

    @Test
    void absentIdAndExactCodeEmitSchemeAbsenceRatherThanNullSuccess() {
        var result = IdentifierSchemeResult.fromAggregate(IdentifierSchemePortContractTest.scheme());
        var useCase = new GetIdentifierSchemeUseCase(new ReadStub(result));
        for (IdentifierSchemeSelector selector : List.of(new IdentifierSchemeSelector.ById(new IdentifierSchemeId(UUID.randomUUID())),
                new IdentifierSchemeSelector.ByCode(result.code().toUpperCase(java.util.Locale.ROOT)),
                new IdentifierSchemeSelector.ByCode(" " + result.code()))) {
            var query = new GetIdentifierSchemeQuery(metadata(), selector);
            var execution = useCase.execute(query).await();
            var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
            assertInstanceOf(ApplicationFailure.IdentifierSchemeNotFound.class, failure.failure());
        }
    }

    @Test
    void preservesDependencyAndUnexpectedFailureInsteadOfMisclassifyingAbsence() {
        var result = IdentifierSchemeResult.fromAggregate(IdentifierSchemePortContractTest.scheme());
        var reads = new ReadStub(result);
        var expected = new ApplicationException(new ApplicationFailure.DependencyUnavailable("catalog"));
        reads.failure = Optional.of(expected);
        var useCase = new GetIdentifierSchemeUseCase(reads);
        var query = new GetIdentifierSchemeQuery(metadata(), new IdentifierSchemeSelector.ById(result.id()));
        var execution = useCase.execute(query).await();
        assertSame(expected, assertThrows(ApplicationException.class, () -> execution.atMost(WAIT)));
        var unexpected = new IllegalStateException("Unexpected catalog corruption");
        reads.failure = Optional.of(unexpected);
        var unexpectedExecution = useCase.execute(query).await();
        assertSame(unexpected, assertThrows(IllegalStateException.class, () -> unexpectedExecution.atMost(WAIT)));
    }

    private static RequestMetadata metadata() {
        return new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());
    }

    /** Captures exact selectors through detached read contracts and rejects any unintended listing. */
    private static final class ReadStub implements IdentifierSchemeReadPort {
        private final IdentifierSchemeResult result;
        private final List<String> calls = new ArrayList<>();
        private Optional<Throwable> failure = Optional.empty();

        private ReadStub(IdentifierSchemeResult result) {
            this.result = result;
        }

        @Override
        public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
            calls.add("id");
            return response(id.equals(result.id()));
        }

        @Override
        public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) {
            calls.add("code:" + code);
            return response(code.equals(result.code()));
        }

        @Override
        public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria,
                Optional<IdentifierSchemePageBoundary> boundary) {
            throw new AssertionError("Retrieval must not load a catalog page");
        }

        private Uni<Optional<IdentifierSchemeResult>> response(boolean found) {
            return failure.<Uni<Optional<IdentifierSchemeResult>>>map(Uni.createFrom()::failure)
                    .orElseGet(() -> Uni.createFrom().item(found ? Optional.of(result) : Optional.empty()));
        }
    }
}
