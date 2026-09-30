package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies exact scoped cursors, tampering rejection, key rotation, and concurrent use of independent MAC instances.
 */
@Timeout(15)
class HmacPartyCursorAdapterTest {

    private static final String KEY_ONE = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private static final String KEY_TWO = Base64.getEncoder()
            .encodeToString("cursor-key-v2-0123456789abcdef01".getBytes(StandardCharsets.UTF_8));
    private static final Instant CREATED = LocalDateTime.of(2026, Month.SEPTEMBER, 19, 12, 0, 0, 123456789)
            .toInstant(ZoneOffset.UTC);
    private static final TenantId TENANT = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final PartyId PARTY = new PartyId(UUID.fromString("80000000-0000-0000-8000-000000000001"));
    private final HmacPartyCursorAdapter adapter = adapter("v1", Map.of("v1", KEY_ONE));

    @ParameterizedTest
    @EnumSource(PartyPageBoundary.Direction.class)
    void roundTripsDirectionExactTimestampAndUnsignedUuidBits(PartyPageBoundary.Direction direction) {
        var boundary = new PartyPageBoundary(direction, new PartyPagePosition(CREATED, PARTY));
        PartySearchScope scope = scope();
        String token = adapter.encode(boundary, scope);

        assertEquals(boundary, adapter.decode(token, scope));
        assertFalse(token.contains("Straße"));
        assertFalse(token.contains(TENANT.value().toString()));
    }

    @Test
    void rejectsMalformedAlteredAndNoncanonicalTokensWithoutEchoingInput() {
        PartySearchScope scope = scope();
        String token = adapter.encode(boundary(), scope);
        String[] parts = token.split("\\.");
        byte[] body = Base64.getUrlDecoder().decode(parts[2]);
        body[0] ^= 1;
        byte[] signature = Base64.getUrlDecoder().decode(parts[3]);
        signature[0] ^= 1;
        String alteredBody = parts[0] + "." + parts[1] + "." + urlEncoded(body) + "." + parts[3];
        String alteredSignature = parts[0] + "." + parts[1] + "." + parts[2] + "." + urlEncoded(signature);

        for (String invalid : List.of("", " ", "not-a-cursor", "x".repeat(257), token + "=", token + ".extra",
                "2." + token.substring(2), "1.unknown." + parts[2] + "." + parts[3], alteredBody, alteredSignature)) {
            assertInvalid(adapter, invalid, scope);
        }
    }

    @Test
    void rejectsChangesToTenantEveryFilterAndPageSize() {
        PartySearchScope expected = scope();
        PartySearchCriteria original = expected.criteria();
        String token = adapter.encode(boundary(), expected);
        var alternatives = new ArrayList<PartySearchScope>();
        alternatives.add(new PartySearchScope(new TenantId(UUID.randomUUID()), original));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(PartyType.LEGAL_ENTITY,
                original.recordStatus(), original.name(), original.createdFrom(), original.createdTo(), original.limit())));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(original.type(),
                PartyRecordStatus.ACTIVE, original.name(), original.createdFrom(), original.createdTo(), original.limit())));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(original.type(), original.recordStatus(),
                new PartyNamePredicate("different", "label"), original.createdFrom(), original.createdTo(), original.limit())));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(original.type(), original.recordStatus(),
                new PartyNamePredicate("Straße", "different"), original.createdFrom(), original.createdTo(), original.limit())));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(original.type(), original.recordStatus(),
                original.name(), CREATED.minusSeconds(2), original.createdTo(), original.limit())));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(original.type(), original.recordStatus(),
                original.name(), original.createdFrom(), CREATED.plusSeconds(2), original.limit())));
        alternatives.add(new PartySearchScope(TENANT, new PartySearchCriteria(original.type(), original.recordStatus(),
                original.name(), original.createdFrom(), original.createdTo(), 200)));
        alternatives.forEach(scope -> assertInvalid(adapter, token, scope));
    }

    @Test
    void acceptsEquivalentCanonicalNamesAndOffsetRepresentations() {
        PartySearchScope expected = scope();
        Instant from = expected.criteria().createdFrom().atOffset(ZoneOffset.UTC)
                .withOffsetSameInstant(ZoneOffset.ofHours(2)).toInstant();
        Instant to = expected.criteria().createdTo().atOffset(ZoneOffset.UTC)
                .withOffsetSameInstant(ZoneOffset.ofHours(-5)).toInstant();
        var equivalent = new PartySearchScope(TENANT, new PartySearchCriteria(PartyType.NATURAL_PERSON,
                PartyRecordStatus.ARCHIVED, new PartyNamePredicate(" STRASSE ", " LABEL "), from, to, 50));
        String token = adapter.encode(boundary(), expected);
        assertEquals(boundary(), adapter.decode(token, equivalent));
    }

    @Test
    void distinguishesDifferentUnpairedSurrogatesInEffectiveScopes() {
        var first = new PartySearchScope(TENANT, new PartySearchCriteria(null, null,
                new PartyNamePredicate("\uD800", null), null, null, 50));
        var second = new PartySearchScope(TENANT, new PartySearchCriteria(null, null,
                new PartyNamePredicate("\uD801", null), null, null, 50));
        String token = adapter.encode(boundary(), first);
        assertInvalid(adapter, token, second);
    }

    @Test
    void rotationRetainsOldVerificationKeysAcrossNewAdapterInstances() {
        PartySearchScope scope = scope();
        String previous = adapter.encode(boundary(), scope);
        var rotated = adapter("v2", Map.of("v1", KEY_ONE, "v2", KEY_TWO));
        var relaunched = adapter("v2", Map.of("v1", KEY_ONE, "v2", KEY_TWO));

        assertEquals(boundary(), rotated.decode(previous, scope));
        assertEquals(boundary(), relaunched.decode(rotated.encode(boundary(), scope), scope));
        assertEquals("v2", rotated.encode(boundary(), scope).split("\\.")[1]);
        assertInvalid(adapter("v2", Map.of("v2", KEY_TWO)), previous, scope);
    }

    @Test
    void supportsConcurrentEncodingAndVerificationWithoutSharingMutableMacs() throws Exception {
        List<Callable<PartyPageBoundary>> tasks = new ArrayList<>();
        PartySearchScope scope = scope();
        PartyPageBoundary boundary = boundary();
        for (int index = 0; index < 40; index++) {
            tasks.add(() -> adapter.decode(adapter.encode(boundary, scope), scope));
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(tasks, 5, TimeUnit.SECONDS)) {
                assertEquals(boundary, result.get(5, TimeUnit.SECONDS));
            }
        }
    }

    private static void assertInvalid(HmacPartyCursorAdapter adapter, String token, PartySearchScope scope) {
        var failure = assertThrows(ApplicationException.class, () -> adapter.decode(token, scope));
        assertInstanceOf(ApplicationFailure.InvalidPartyCursor.class, failure.failure());
        assertEquals("Invalid Party cursor", failure.getMessage());
        assertNull(failure.getCause());
    }

    private static HmacPartyCursorAdapter adapter(String current, Map<String, String> keys) {
        return new HmacPartyCursorAdapter(new PartyCursorKeyMaterial(new TestConfiguration(Optional.of(current), keys)));
    }

    private static String urlEncoded(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static PartyPageBoundary boundary() {
        return new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, new PartyPagePosition(CREATED, PARTY));
    }

    private static PartySearchScope scope() {
        return new PartySearchScope(TENANT, new PartySearchCriteria(PartyType.NATURAL_PERSON,
                PartyRecordStatus.ARCHIVED, new PartyNamePredicate("Straße", "label"),
                CREATED.minusSeconds(1), CREATED.plusSeconds(1), 50));
    }

    /** Supplies independently constructed test-only signing rings for rotation and relaunch checks. */
    private record TestConfiguration(Optional<String> currentSigningKeyId, Map<String, String> signingKeys)
            implements PartyPaginationConfiguration {
    }
}
