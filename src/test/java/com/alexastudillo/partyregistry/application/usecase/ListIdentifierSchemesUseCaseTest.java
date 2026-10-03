package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.query.ListIdentifierSchemesQuery;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies exact filter/scope forwarding, authentication precedence, and bounded detached page assembly. */
class ListIdentifierSchemesUseCaseTest {
    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());

    @Test
    void unfilteredPageRetainsAllStatesAndEmptyPageHasNoNavigationOrTotals() {
        var reads = new ReadStub();
        var cursors = new CursorStub();
        var useCase = new ListIdentifierSchemesUseCase(reads, cursors);
        var criteria = new IdentifierSchemeSearchCriteria(null, null, null, null, IdentifierSchemeSearchCriteria.DEFAULT_LIMIT);
        var items = java.util.Arrays.stream(IdentifierSchemeStatus.values()).map(ListIdentifierSchemesUseCaseTest::result).toList();
        reads.slice = new IdentifierSchemePageSlice(items, Optional.empty(), Optional.empty());
        var query = new ListIdentifierSchemesQuery(METADATA, criteria, Optional.empty());
        var deferred = useCase.execute(query);
        assertTrue(reads.calls.isEmpty());
        var page = deferred.await().atMost(WAIT);
        assertEquals(items, page.items());
        assertTrue(page.nextCursor().isEmpty());
        assertTrue(page.previousCursor().isEmpty());
        assertEquals(List.of(criteria), reads.calls);
        assertTrue(cursors.encoded.isEmpty());
        reads.slice = new IdentifierSchemePageSlice(List.of(), Optional.empty(), Optional.empty());
        assertTrue(useCase.execute(query).await().atMost(WAIT).items().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(IdentifierSchemePageBoundary.Direction.class)
    void forwardsDecodedDirectionAndAuthenticatesOnlyAvailableNavigation(IdentifierSchemePageBoundary.Direction direction) {
        var reads = new ReadStub();
        var cursors = new CursorStub();
        var criteria = new IdentifierSchemeSearchCriteria("EC", IdentifierCategory.OTHER, IdentifierSubjectType.NATURAL_PERSON,
                IdentifierSchemeStatus.ACTIVE, 1);
        var scope = new IdentifierSchemeSearchScope(METADATA.tenantId(), criteria);
        var row = result(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.NATURAL_PERSON);
        var position = new IdentifierSchemePagePosition(row.createdAt(), row.id());
        var incoming = new IdentifierSchemePageBoundary(direction, position);
        var token = cursors.encode(incoming, scope);
        cursors.encoded.clear();
        reads.slice = new IdentifierSchemePageSlice(List.of(row), Optional.of(position), Optional.of(position));
        var page = new ListIdentifierSchemesUseCase(reads, cursors).execute(
                new ListIdentifierSchemesQuery(METADATA, criteria, Optional.of(token))).await().atMost(WAIT);
        assertEquals(Optional.of(incoming), reads.lastBoundary);
        assertEquals(List.of(criteria), reads.calls);
        assertEquals(IdentifierSubjectType.NATURAL_PERSON, reads.calls.getFirst().applicableSubjectType());
        assertEquals(List.of(new SavedCursor(new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT, position), scope),
                new SavedCursor(new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.PREVIOUS, position), scope)), cursors.encoded);
        assertEquals(IdentifierSchemePageBoundary.Direction.NEXT, cursors.decode(page.nextCursor().orElseThrow(), scope).direction());
        assertEquals(IdentifierSchemePageBoundary.Direction.PREVIOUS, cursors.decode(page.previousCursor().orElseThrow(), scope).direction());
    }

    @Test
    void rejectsTokenAndEveryScopeChangeBeforeReadingTheCatalog() {
        var reads = new ReadStub();
        var cursors = new CursorStub();
        var criteria = new IdentifierSchemeSearchCriteria("EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                IdentifierSchemeStatus.DRAFT, 50);
        var row = result(IdentifierSchemeStatus.DRAFT);
        var boundary = new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT,
                new IdentifierSchemePagePosition(row.createdAt(), row.id()));
        var token = cursors.encode(boundary, new IdentifierSchemeSearchScope(METADATA.tenantId(), criteria));
        var useCase = new ListIdentifierSchemesUseCase(reads, cursors);
        for (var changed : List.of(
                new IdentifierSchemeSearchCriteria("US", criteria.category(), criteria.applicableSubjectType(), criteria.status(), 50),
                new IdentifierSchemeSearchCriteria("EC", IdentifierCategory.PASSPORT, criteria.applicableSubjectType(), criteria.status(), 50),
                new IdentifierSchemeSearchCriteria("EC", criteria.category(), IdentifierSubjectType.NATURAL_PERSON, criteria.status(), 50),
                new IdentifierSchemeSearchCriteria("EC", criteria.category(), criteria.applicableSubjectType(), IdentifierSchemeStatus.RETIRED, 50),
                new IdentifierSchemeSearchCriteria("EC", criteria.category(), criteria.applicableSubjectType(), criteria.status(), 200))) {
            assertInvalid(useCase, new ListIdentifierSchemesQuery(METADATA, changed, Optional.of(token)));
        }
        assertInvalid(useCase, new ListIdentifierSchemesQuery(new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID()),
                criteria, Optional.of(token)));
        assertInvalid(useCase, new ListIdentifierSchemesQuery(METADATA, criteria, Optional.of("tampered")));
        assertTrue(reads.calls.isEmpty());
    }

    @Test
    void acceptsMaximumPageSizeButRejectsAnAdapterExceedingTheEffectiveLimit() {
        var reads = new ReadStub();
        var row = result(IdentifierSchemeStatus.DRAFT);
        reads.slice = new IdentifierSchemePageSlice(java.util.Collections.nCopies(200, row), Optional.empty(), Optional.empty());
        var useCase = new ListIdentifierSchemesUseCase(reads, new CursorStub());
        var validQuery = new ListIdentifierSchemesQuery(METADATA,
                new IdentifierSchemeSearchCriteria(null, null, null, null, 200), Optional.empty());
        assertEquals(200, useCase.execute(validQuery).await().atMost(WAIT).items().size());
        var exceedingQuery = new ListIdentifierSchemesQuery(METADATA,
                new IdentifierSchemeSearchCriteria(null, null, null, null, 1), Optional.empty());
        var exceedingExecution = useCase.execute(exceedingQuery).await();
        assertThrows(IllegalStateException.class, () -> exceedingExecution.atMost(WAIT));
    }

    private static void assertInvalid(ListIdentifierSchemesUseCase useCase, ListIdentifierSchemesQuery query) {
        var execution = useCase.execute(query).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeCursor.class, failure.failure());
    }

    private static IdentifierSchemeResult result(IdentifierSchemeStatus status) {
        return result(status, IdentifierSubjectType.BOTH);
    }

    private static IdentifierSchemeResult result(IdentifierSchemeStatus status, IdentifierSubjectType subjectType) {
        var base = IdentifierSchemePortContractTest.scheme();
        return IdentifierSchemeResult.fromAggregate(new IdentifierScheme(base.id(), base.code(), base.issuingCountryCode(),
                base.category(), subjectType, base.name(), base.description(), base.normalizerKey(), base.validatorKey(),
                base.minimumLength(), base.maximumLength(), base.requiresExpiration(), status, base.version(), base.auditInfo()));
    }

    /** Records exact read criteria and a detached bounded slice without implementing database filtering. */
    private static final class ReadStub implements IdentifierSchemeReadPort {
        private final List<IdentifierSchemeSearchCriteria> calls = new ArrayList<>();
        private Optional<IdentifierSchemePageBoundary> lastBoundary = Optional.empty();
        private IdentifierSchemePageSlice slice = new IdentifierSchemePageSlice(List.of(), Optional.empty(), Optional.empty());

        @Override
        public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
            throw new AssertionError("Listing must not retrieve individual schemes");
        }

        @Override
        public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) {
            throw new AssertionError("Listing must not retrieve individual schemes");
        }

        @Override
        public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria,
                Optional<IdentifierSchemePageBoundary> boundary) {
            calls.add(criteria);
            lastBoundary = boundary;
            return Uni.createFrom().item(slice);
        }
    }

    /** Carries the complete inner navigation identity for deterministic orchestration verification. */
    private record SavedCursor(IdentifierSchemePageBoundary boundary, IdentifierSchemeSearchScope scope) { }

    /** Simulates opaque scope-bound tokens; cryptographic verification belongs to adapter tests. */
    private static final class CursorStub implements IdentifierSchemeCursorPort {
        private final Map<String, SavedCursor> tokens = new HashMap<>();
        private final List<SavedCursor> encoded = new ArrayList<>();

        @Override
        public IdentifierSchemePageBoundary decode(String token, IdentifierSchemeSearchScope scope) {
            var saved = tokens.get(token);
            if (saved == null || !saved.scope().equals(scope)) {
                throw new ApplicationException(new ApplicationFailure.InvalidIdentifierSchemeCursor());
            }
            return saved.boundary();
        }

        @Override
        public String encode(IdentifierSchemePageBoundary boundary, IdentifierSchemeSearchScope scope) {
            var saved = new SavedCursor(boundary, scope);
            encoded.add(saved);
            var token = UUID.randomUUID().toString();
            tokens.put(token, saved);
            return token;
        }
    }
}
