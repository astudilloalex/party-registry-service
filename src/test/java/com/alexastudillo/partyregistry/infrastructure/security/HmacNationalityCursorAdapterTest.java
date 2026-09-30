package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPagePosition;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;
import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks signed nationality-only continuations and complete effective search
 * scoping.
 */
class HmacNationalityCursorAdapterTest {

    private static final String KEY = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private static final Instant CREATED = Instant.parse("2026-09-23T10:00:00.123456789Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());
    private final PartyCursorKeyMaterial ring = new PartyCursorKeyMaterial(
            new TestConfiguration(Optional.of("v1"), Map.of("v1", KEY)));
    private final HmacNationalityCursorAdapter adapter = new HmacNationalityCursorAdapter(ring);

    @Test
    void roundTripsBothDirectionsAndRejectsRootAndAlteredTokens() {
        NationalitySearchScope scope = scope();
        for (NationalityPageBoundary.Direction direction : NationalityPageBoundary.Direction.values()) {
            NationalityPageBoundary boundary = new NationalityPageBoundary(direction,
                    new NationalityPagePosition(CREATED, new NationalityId(UUID.randomUUID())));
            String token = adapter.encode(boundary, scope);
            assertEquals(boundary, adapter.decode(token, scope));
            assertInvalid(token + "=", scope);
            assertInvalid(token.substring(0, token.length() - 1), scope);
            String[] parts = token.split("\\.");
            assertInvalid(parts[0] + "." + parts[1] + "." + parts[2] + "." + "A".repeat(43), scope);
        }
        var rootAdapter = new HmacPartyCursorAdapter(ring);
        String rootToken = rootAdapter.encode(new PartyPageBoundary(PartyPageBoundary.Direction.NEXT,
                new PartyPagePosition(CREATED, PARTY)),
                new PartySearchScope(TENANT,
                        new PartySearchCriteria(null, null, new PartyNamePredicate(null, null),
                                null, null, 50)));
        assertInvalid(rootToken, scope);
        assertInvalid("", scope);
        assertInvalid("x".repeat(257), scope);
    }

    @Test
    void rejectsChangesToTenantPartyFiltersDateAndLimit() {
        String token = adapter.encode(new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT,
                new NationalityPagePosition(CREATED, new NationalityId(UUID.randomUUID()))), scope());
        var original = scope().criteria();
        List<NationalitySearchScope> alternatives = new ArrayList<>();
        alternatives.add(new NationalitySearchScope(new TenantId(UUID.randomUUID()), PARTY, original));
        alternatives.add(new NationalitySearchScope(TENANT, new PartyId(UUID.randomUUID()), original));
        alternatives.add(new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("CO", true, original.asOfDate(), false, 50)));
        alternatives.add(new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("EC", false, original.asOfDate(), false, 50)));
        alternatives.add(new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("EC", null, original.asOfDate(), false, 50)));
        alternatives.add(new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("EC", true, original.asOfDate().plusDays(1), false, 50)));
        alternatives.add(new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("EC", true, original.asOfDate(), true, 50)));
        alternatives.add(new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("EC", true, original.asOfDate(), false, 200)));
        alternatives.forEach(other -> assertInvalid(token, other));
    }

    private void assertInvalid(String token, NationalitySearchScope scope) {
        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> adapter.decode(token, scope));
        assertInstanceOf(ApplicationFailure.InvalidNationalityCursor.class, failure.failure());
    }

    private static NationalitySearchScope scope() {
        return new NationalitySearchScope(TENANT, PARTY,
                new NationalitySearchCriteria("EC", true, LocalDate.of(2026, Month.SEPTEMBER, 23),
                        false, 50));
    }

    /**
     * Supplies an independent test key ring without modifying production
     * configuration.
     */
    private record TestConfiguration(Optional<String> currentSigningKeyId, Map<String, String> signingKeys)
            implements PartyPaginationConfiguration {
    }
}
