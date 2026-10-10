package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchCursorPort;
import com.alexastudillo.partyregistry.application.support.IdentifierSearchReadStub;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelectorPolicy;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies exact cardinality, neutral selector failures and prefix scope/continuation precedence with independent port doubles. */
class IdentifierSearchUseCasesTest {
    private static final TenantId TENANT = new TenantId(new UUID(1, 1));
    private final IdentifierSearchSelectorPolicy policy = new IdentifierSearchSelectorPolicy(new IdentifierRuleCatalog());

    @Test
    void exactReturnsAll201HistoricalHitsAndExplicitNullDirections() {
        var items = new ArrayList<IdentifierSearchHit>();
        for (int id = 201; id > 0; id--) items.add(hit(id));
        var reads = stub(Optional.of(scheme("TRIM_UPPERCASE_V1")), new ExactIdentifierSearchSlice(items, 201), emptyPrefix());
        var useCase = new SearchPartiesByIdentifierUseCase(reads, policy);
        var page = item(useCase.execute(new ExactIdentifierSearchQuery(TENANT, "SEARCH", " short ", IdentifierSearchFilters.of(null, null))));
        assertEquals(201, page.totalElements());
        assertEquals(201, page.numberOfElements());
        assertEquals(1, page.totalPages());
        assertNull(page.nextCursor());
        assertNull(page.previousCursor());
    }

    @Test
    void exactResolvesSchemeAndSupportedNormalizationBeforeEmptyResults() {
        var query = new ExactIdentifierSearchQuery(TENANT, "SEARCH", "A", IdentifierSearchFilters.of(List.of(), null));
        var absent = new SearchPartiesByIdentifierUseCase(stub(Optional.empty(), emptyExact(), emptyPrefix()), policy);
        assertInstanceOf(ApplicationFailure.UnknownIdentifierScheme.class, failure(absent.execute(query)).failure());
        var unsupported = new SearchPartiesByIdentifierUseCase(stub(Optional.of(scheme("UNSUPPORTED")), emptyExact(), emptyPrefix()), policy);
        assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(unsupported.execute(query)).failure());
        var supported = new SearchPartiesByIdentifierUseCase(stub(Optional.of(scheme("TRIM_UPPERCASE_V1")), emptyExact(), emptyPrefix()), policy);
        assertEquals(0, item(supported.execute(query)).totalPages());
    }

    @Test
    void prefixUnknownSchemeAndUnsupportedNormalizerPrecedeAlteredCursor() {
        var cursors = new CursorFixture();
        var query = new PrefixIdentifierSearchQuery(TENANT, "SEARCH", "A", IdentifierSearchFilters.of(null, null), 2, "ALTERED");
        var absent = new SearchPartiesByIdentifierPrefixUseCase(stub(Optional.empty(), emptyExact(), emptyPrefix()), cursors, policy);
        assertInstanceOf(ApplicationFailure.UnknownIdentifierScheme.class, failure(absent.execute(query)).failure());
        var unsupported = new SearchPartiesByIdentifierPrefixUseCase(stub(Optional.of(scheme("UNSUPPORTED")), emptyExact(), emptyPrefix()), cursors, policy);
        assertInstanceOf(ApplicationFailure.IdentifierValidationFailure.class, failure(unsupported.execute(query)).failure());
        assertEquals(0, cursors.decodes);
    }

    @Test
    void prefixAssemblesExactMetadataAndOnlyAvailableDirections() {
        var cursors = new CursorFixture();
        var slice = new PrefixIdentifierSearchSlice(List.of(hit(5), hit(4)), 5, Optional.of(hit(4).position()), Optional.empty());
        var useCase = new SearchPartiesByIdentifierPrefixUseCase(stub(Optional.of(scheme("TRIM_UPPERCASE_V1")), emptyExact(), slice), cursors, policy);
        var page = item(useCase.execute(new PrefixIdentifierSearchQuery(TENANT, "SEARCH", "  a ", IdentifierSearchFilters.of(null, null), 2, null)));
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(2, page.numberOfElements());
        assertEquals("VALID", page.nextCursor());
        assertNull(page.previousCursor());
        var continuation = new PrefixIdentifierSearchQuery(TENANT, "SEARCH", "A", IdentifierSearchFilters.of(
                List.of(PartyIdentifierStatus.values()), List.of(PartyRecordStatus.values())), 2, page.nextCursor());
        assertEquals(5, item(useCase.execute(continuation)).totalElements());
        assertEquals(1, cursors.decodes);
        var changed = new PrefixIdentifierSearchQuery(TENANT, "SEARCH", "A", IdentifierSearchFilters.of(List.of(), null), 2, "VALID");
        assertInstanceOf(ApplicationFailure.InvalidIdentifierCursor.class, failure(useCase.execute(changed)).failure());
    }

    private static IdentifierSearchReadStub stub(Optional<IdentifierScheme> scheme, ExactIdentifierSearchSlice exact, PrefixIdentifierSearchSlice prefix) {
        return new IdentifierSearchReadStub(scheme, exact, prefix);
    }

    private static ExactIdentifierSearchSlice emptyExact() { return new ExactIdentifierSearchSlice(List.of(), 0); }
    private static PrefixIdentifierSearchSlice emptyPrefix() { return new PrefixIdentifierSearchSlice(List.of(), 0, Optional.empty(), Optional.empty()); }

    private static IdentifierSearchHit hit(int id) {
        return new IdentifierSearchHit(new PartyId(new UUID(2, 2)), PartyType.LEGAL_ENTITY, "Stored", PartyRecordStatus.ARCHIVED,
                "SEARCH", "***001", PartyIdentifierStatus.REVOKED, false,
                new PartyIdentifierPagePosition(Instant.EPOCH, new PartyIdentifierId(new UUID(0, id))));
    }

    private static IdentifierScheme scheme(String normalizer) {
        return new IdentifierScheme(new IdentifierSchemeId(new UUID(3, 3)), "SEARCH", "EC", IdentifierCategory.OTHER,
                IdentifierSubjectType.NATURAL_PERSON, "Historical", null, normalizer, "UNSUPPORTED_VALIDATOR", 10, 10, true,
                IdentifierSchemeStatus.RETIRED, IdentifierSchemeVersion.initial(), AuditInfo.initial(Instant.EPOCH, "test"));
    }

    private static IdentifierSearchPage item(io.smallrye.mutiny.Uni<IdentifierSearchPage> operation) {
        return operation.subscribe().withSubscriber(UniAssertSubscriber.<IdentifierSearchPage>create()).awaitItem(Duration.ofSeconds(2)).getItem();
    }

    private static ApplicationException failure(io.smallrye.mutiny.Uni<IdentifierSearchPage> operation) {
        return assertInstanceOf(ApplicationException.class, operation.subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(Duration.ofSeconds(2)).getFailure());
    }

    /** Simulates authenticated effective scope to verify orchestration without coupling Application tests to JCA. */
    private static final class CursorFixture implements IdentifierSearchCursorPort {
        private Optional<IdentifierSearchScope> expected = Optional.empty();
        private int decodes;

        @Override
        public PartyIdentifierPageBoundary decode(IdentifierSearchScope scope, String token) {
            decodes++;
            if (!"VALID".equals(token) || !expected.equals(Optional.of(scope))) {
                throw new ApplicationException(new ApplicationFailure.InvalidIdentifierCursor());
            }
            return new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, hit(4).position());
        }

        @Override
        public String encode(IdentifierSearchScope scope, PartyIdentifierPageBoundary boundary) {
            expected = Optional.of(scope);
            return "VALID";
        }
    }
}
