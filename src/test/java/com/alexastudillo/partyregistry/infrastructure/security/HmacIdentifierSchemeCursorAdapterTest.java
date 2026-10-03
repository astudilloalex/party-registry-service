package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPagePosition;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;
import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.query.ListIdentifierSchemesQuery;
import com.alexastudillo.partyregistry.application.usecase.ListIdentifierSchemesUseCase;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies authenticated scheme-only navigation, strict bounded decoding,
 * rotation, and rejection before reads.
 */
@Timeout(15)
class HmacIdentifierSchemeCursorAdapterTest {
    private static final String KEY_ONE = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private static final String KEY_TWO = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final Instant CREATED = Instant.parse("2026-10-01T10:00:00.123456Z");
    private static final TenantId TENANT = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final IdentifierSchemeId SCHEME = new IdentifierSchemeId(
            UUID.fromString("80000000-0000-0000-8000-000000000001"));
    private final HmacIdentifierSchemeCursorAdapter adapter = adapter("v1", Map.of("v1", KEY_ONE));

    @ParameterizedTest
    @EnumSource(IdentifierSchemePageBoundary.Direction.class)
    void roundTripsBothDirectionsExactMicrosecondsAndAllUuidBits(IdentifierSchemePageBoundary.Direction direction) {
        for (Instant time : List.of(CREATED, Instant.EPOCH, Instant.ofEpochSecond(-1, 999_999_000),
                Instant.MIN, Instant.MAX.minusNanos(999))) {
            var boundary = new IdentifierSchemePageBoundary(direction, new IdentifierSchemePagePosition(time, SCHEME));
            for (var scope : List.of(scope(), scope(null, null, null, null, 1), scope(null, null, null, null, 200))) {
                String token = adapter.encode(boundary, scope);
                assertEquals(boundary, adapter.decode(token, scope));
                assertEquals(token, adapter.encode(adapter.decode(token, scope), scope));
                assertTrue(token.length() <= 256);
                assertFalse(token.contains("="));
                assertFalse(token.contains(TENANT.value().toString()));
                assertFalse(token.contains(SCHEME.value().toString()));
            }
        }
    }

    @Test
    void bindsTenantEveryExactFilterPresenceAndEffectiveLimit() {
        String token = adapter.encode(boundary(), scope());
        var alternatives = new ArrayList<IdentifierSchemeSearchScope>();
        alternatives.add(new IdentifierSchemeSearchScope(new TenantId(UUID.randomUUID()), scope().criteria()));
        alternatives.add(
                scope("US", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.DRAFT, 50));
        alternatives.add(
                scope(null, IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.DRAFT, 50));
        alternatives.add(scope("EC", null, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.DRAFT, 50));
        alternatives.add(scope("EC", IdentifierCategory.OTHER, null, IdentifierSchemeStatus.DRAFT, 50));
        alternatives.add(scope("EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, null, 50));
        for (var category : IdentifierCategory.values()) {
            if (category != IdentifierCategory.OTHER) {
                alternatives.add(scope("EC", category, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.DRAFT, 50));
            }
        }
        for (var subject : IdentifierSubjectType.values()) {
            if (subject != IdentifierSubjectType.BOTH) {
                alternatives.add(scope("EC", IdentifierCategory.OTHER, subject, IdentifierSchemeStatus.DRAFT, 50));
            }
        }
        for (var status : IdentifierSchemeStatus.values()) {
            if (status != IdentifierSchemeStatus.DRAFT) {
                alternatives.add(scope("EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, status, 50));
            }
        }
        for (int limit : List.of(1, 49, 51, 200)) {
            alternatives.add(scope("EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                    IdentifierSchemeStatus.DRAFT, limit));
        }
        alternatives.forEach(changed -> assertInvalid(adapter, token, changed));
        String unfiltered = adapter.encode(boundary(), scope(null, null, null, null, 50));
        assertInvalid(adapter, unfiltered, scope());
    }

    @Test
    void rejectsMalformedOversizedTruncatedAndNoncanonicalEncoding() {
        String token = adapter.encode(boundary(), scope());
        String[] parts = token.split("\\.");
        var invalid = new ArrayList<>(List.of("", " ", "not-a-cursor", "x".repeat(257), "x".repeat(100_000),
                token + "=", token + ".extra", " " + token, token + "\n", "2." + token.substring(2),
                "01." + token.substring(2), "1.." + parts[2] + "." + parts[3],
                "1.invalid!." + parts[2] + "." + parts[3], "1." + "k".repeat(65) + "." + parts[2] + "." + parts[3],
                "1.v1.." + parts[3], "1.v1." + parts[2] + ".", "1.v1.*." + parts[3],
                "1.v1." + parts[2] + "=." + parts[3], "1.v1." + noncanonical(parts[2]) + "." + parts[3],
                "1.v1." + parts[2] + "." + noncanonical(parts[3])));
        for (int length = 1; length < token.length(); length++) {
            invalid.add(token.substring(0, length));
        }
        invalid.forEach(value -> assertInvalid(adapter, value, scope()));
        assertInvalid(adapter, null, scope());
    }

    @Test
    void authenticatesEveryPayloadAndSignatureByteIncludingDirectionTupleAndScope() {
        String token = adapter.encode(boundary(), scope());
        String[] parts = token.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[2]);
        byte[] signature = Base64.getUrlDecoder().decode(parts[3]);
        for (int index = 0; index < payload.length; index++) {
            byte[] changed = payload.clone();
            changed[index] ^= 1;
            assertInvalid(adapter, "1.v1." + url(changed) + "." + parts[3], scope());
        }
        for (int index = 0; index < signature.length; index++) {
            byte[] changed = signature.clone();
            changed[index] ^= 1;
            assertInvalid(adapter, "1.v1." + parts[2] + "." + url(changed), scope());
        }
    }

    @Test
    void rejectsAuthenticatedInvalidDirectionsTimestampsAndPayloadLengths() throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(adapter.encode(boundary(), scope()).split("\\.")[2]);
        for (byte direction : new byte[] { 2, -1, 127 }) {
            byte[] changed = payload.clone();
            changed[0] = direction;
            assertInvalid(adapter, signed("1", "v1", changed, KEY_ONE), scope());
        }
        for (int nanos : new int[] { -1, 1, 123_456_789, 1_000_000_000, Integer.MAX_VALUE }) {
            byte[] changed = payload.clone();
            ByteBuffer.wrap(changed).putInt(9, nanos);
            assertInvalid(adapter, signed("1", "v1", changed, KEY_ONE), scope());
        }
        for (long seconds : new long[] { Long.MIN_VALUE, Long.MAX_VALUE }) {
            byte[] changed = payload.clone();
            ByteBuffer.wrap(changed).putLong(1, seconds);
            assertInvalid(adapter, signed("1", "v1", changed, KEY_ONE), scope());
        }
        for (int limit : new int[] { 0, 51, 201, Integer.MAX_VALUE }) {
            byte[] changed = payload.clone();
            ByteBuffer.wrap(changed).putInt(29, limit);
            assertInvalid(adapter, signed("1", "v1", changed, KEY_ONE), scope());
        }
        assertInvalid(adapter, signed("1", "v1", java.util.Arrays.copyOf(payload, 64), KEY_ONE), scope());
        assertInvalid(adapter, signed("1", "v1", java.util.Arrays.copyOf(payload, 66), KEY_ONE), scope());
        assertInvalid(adapter, signed("2", "v1", payload, KEY_ONE), scope());
        var nonMicroBoundary = new IdentifierSchemePageBoundary(
                IdentifierSchemePageBoundary.Direction.NEXT,
                new IdentifierSchemePagePosition(CREATED.plusNanos(1), SCHEME));
        var nonMicroScope = scope();
        assertThrows(IllegalArgumentException.class, () -> adapter.encode(nonMicroBoundary, nonMicroScope));
    }

    @Test
    void reusesRetainedKeysAcrossRelaunchAndAuthenticatesKeyIdentity() throws Exception {
        String old = adapter.encode(boundary(), scope());
        var rotated = adapter("v2", Map.of("v1", KEY_ONE, "v2", KEY_TWO));
        var relaunched = adapter("v2", Map.of("v1", KEY_ONE, "v2", KEY_TWO));
        assertEquals(boundary(), rotated.decode(old, scope()));
        String current = rotated.encode(boundary(), scope());
        assertEquals("v2", current.split("\\.")[1]);
        assertEquals(boundary(), relaunched.decode(current, scope()));
        assertInvalid(adapter("v2", Map.of("v2", KEY_TWO)), old, scope());
        assertInvalid(adapter("v1", Map.of("v1", KEY_TWO)), old, scope());
        byte[] payload = Base64.getUrlDecoder().decode(old.split("\\.")[2]);
        assertInvalid(adapter, signed("1", "unknown", payload, KEY_ONE), scope());
        var aliases = adapter("v1", Map.of("v1", KEY_ONE, "alias", KEY_ONE));
        assertInvalid(aliases, old.replace(".v1.", ".alias."), scope());
        String maximumKey = "k".repeat(64);
        var maximum = adapter(maximumKey, Map.of(maximumKey, KEY_ONE));
        assertEquals(boundary(), maximum.decode(maximum.encode(boundary(), scope()), scope()));
    }

    @Test
    void rejectsPartyAndNationalityTokensEvenWithIdenticalSigningKeysAndTuples() {
        var ring = ring("v1", Map.of("v1", KEY_ONE));
        var party = new PartyId(SCHEME.value());
        var partyScope = new PartySearchScope(TENANT,
                new PartySearchCriteria(null, null, new PartyNamePredicate(null, null), null, null, 50));
        var nationalityScope = new NationalitySearchScope(TENANT, party,
                new NationalitySearchCriteria(null, null, LocalDate.of(2026, Month.OCTOBER, 1), false, 50));
        var parties = new HmacPartyCursorAdapter(ring);
        var nationalities = new HmacNationalityCursorAdapter(ring);
        for (var direction : IdentifierSchemePageBoundary.Direction.values()) {
            String partyToken = parties
                    .encode(new PartyPageBoundary(PartyPageBoundary.Direction.valueOf(direction.name()),
                            new PartyPagePosition(CREATED, party)), partyScope);
            String nationalityToken = nationalities.encode(new NationalityPageBoundary(
                    NationalityPageBoundary.Direction.valueOf(direction.name()),
                    new NationalityPagePosition(CREATED, new NationalityId(SCHEME.value()))), nationalityScope);
            assertInvalid(adapter, partyToken, scope());
            assertInvalid(adapter, nationalityToken, scope());
            String schemeToken = adapter.encode(new IdentifierSchemePageBoundary(direction, boundary().position()),
                    scope());
            assertInstanceOf(ApplicationFailure.InvalidPartyCursor.class,
                    assertThrows(ApplicationException.class, () -> parties.decode(schemeToken, partyScope)).failure());
            assertInstanceOf(ApplicationFailure.InvalidNationalityCursor.class,
                    assertThrows(ApplicationException.class, () -> nationalities.decode(schemeToken, nationalityScope))
                            .failure());
        }
    }

    @Test
    void concurrentCallsUseIndependentMacInstances() throws Exception {
        List<Callable<IdentifierSchemePageBoundary>> work = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            work.add(() -> adapter.decode(adapter.encode(boundary(), scope()), scope()));
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(work, 5, TimeUnit.SECONDS)) {
                assertEquals(boundary(), result.get(5, TimeUnit.SECONDS));
            }
        }
    }

    private static void assertInvalid(HmacIdentifierSchemeCursorAdapter adapter, @Nullable String token,
            IdentifierSchemeSearchScope scope) {
        var failure = assertThrows(ApplicationException.class, () -> adapter.decode(token, scope));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeCursor.class, failure.failure());
        assertEquals("Invalid identifier scheme cursor", failure.getMessage());
        assertNull(failure.getCause());
        if (token != null) {
            var reads = new RejectingReadPort();
            var useCase = new ListIdentifierSchemesUseCase(reads, adapter);
            var query = new ListIdentifierSchemesQuery(
                    new RequestMetadata(scope.tenantId(), "reader", UUID.randomUUID()),
                    scope.criteria(), Optional.of(token));
            var execution = useCase.execute(query).await();
            var timeout = Duration.ofSeconds(2);
            var rejected = assertThrows(ApplicationException.class, () -> execution.atMost(timeout));
            assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeCursor.class, rejected.failure());
            assertEquals(0, reads.calls);
        }
    }

    private static IdentifierSchemePageBoundary boundary() {
        return new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT,
                new IdentifierSchemePagePosition(CREATED, SCHEME));
    }

    private static IdentifierSchemeSearchScope scope() {
        return scope("EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.DRAFT, 50);
    }

    private static IdentifierSchemeSearchScope scope(@Nullable String country, @Nullable IdentifierCategory category,
            @Nullable IdentifierSubjectType subject, @Nullable IdentifierSchemeStatus status, int limit) {
        return new IdentifierSchemeSearchScope(TENANT,
                new IdentifierSchemeSearchCriteria(country, category, subject, status, limit));
    }

    private static PartyCursorKeyMaterial ring(String current, Map<String, String> keys) {
        return new PartyCursorKeyMaterial(new TestConfiguration(Optional.of(current), keys));
    }

    private static HmacIdentifierSchemeCursorAdapter adapter(String current, Map<String, String> keys) {
        return new HmacIdentifierSchemeCursorAdapter(ring(current, keys));
    }

    private static String url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String noncanonical(String encoded) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int last = encoded.length() - 1;
        return encoded.substring(0, last) + alphabet.charAt(alphabet.indexOf(encoded.charAt(last)) ^ 1);
    }

    private static String signed(String version, String keyId, byte[] payload, String key) throws Exception {
        String signed = version + "." + keyId + "." + url(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
        mac.update("identifier-scheme-list-cursor-v1\0".getBytes(StandardCharsets.US_ASCII));
        return signed + "." + url(mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Fails immediately if a rejected continuation reaches any business read
     * capability.
     */
    private static final class RejectingReadPort implements IdentifierSchemeReadPort {
        private int calls;

        @Override
        public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
            calls++;
            throw new AssertionError("Rejected cursor must not read schemes");
        }

        @Override
        public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) {
            calls++;
            throw new AssertionError("Rejected cursor must not read schemes");
        }

        @Override
        public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria,
                Optional<IdentifierSchemePageBoundary> boundary) {
            calls++;
            throw new AssertionError("Rejected cursor must not read schemes");
        }
    }

    /**
     * Supplies isolated test keys without introducing production defaults or
     * changing the shared configuration.
     */
    private record TestConfiguration(Optional<String> currentSigningKeyId, Map<String, String> signingKeys)
            implements PartyPaginationConfiguration {
    }
}
