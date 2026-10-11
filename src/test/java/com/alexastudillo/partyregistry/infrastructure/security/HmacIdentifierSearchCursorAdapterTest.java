package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchFilters;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.policy.IdentifierNormalizer;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies keyed sensitive scope, canonical bounded parsing, resource isolation and retained signing-key rotation. */
class HmacIdentifierSearchCursorAdapterTest {
    private static final String KEY = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private static final TenantId TENANT = new TenantId(new UUID(1, 1));
    private static final IdentifierSchemeId SCHEME = new IdentifierSchemeId(new UUID(2, 2));
    private final PartyCursorKeyMaterial ring = ring("v1", Map.of("v1", KEY));
    private final HmacIdentifierSearchCursorAdapter adapter = new HmacIdentifierSearchCursorAdapter(ring);

    @Test
    void roundTripsBothDirectionsFullPrecisionAndCanonicalEffectiveSets() {
        for (var direction : PartyIdentifierPageBoundary.Direction.values()) {
            var boundary = boundary(direction);
            String token = adapter.encode(scope(), boundary);
            assertEquals(boundary, adapter.decode(scope(), token));
            assertTrue(token.length() <= 256);
            assertFalse(token.contains("PRIVATE"));
            var identifiers = new java.util.ArrayList<>(Arrays.asList(PartyIdentifierStatus.values()));
            identifiers.add(PartyIdentifierStatus.VERIFIED);
            java.util.Collections.reverse(identifiers);
            var equivalent = new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", selector(" private "),
                    IdentifierSearchFilters.of(identifiers, Arrays.asList(PartyRecordStatus.values())), 50);
            assertEquals(boundary, adapter.decode(equivalent, token));
        }
    }

    @Test
    void rejectsEachChangedScopeDimensionAndTokensFromOtherResources() {
        var original = scope();
        String token = adapter.encode(original, boundary(PartyIdentifierPageBoundary.Direction.NEXT));
        for (var changed : List.of(
                new IdentifierSearchScope(new TenantId(new UUID(3, 3)), SCHEME, "SEARCH", original.selector(), original.filters(), 50),
                new IdentifierSearchScope(TENANT, new IdentifierSchemeId(new UUID(4, 4)), "SEARCH", original.selector(), original.filters(), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "search", original.selector(), original.filters(), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", selector("OTHER"), original.filters(), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", new IdentifierSearchSelector(ChangedNormalizer.KEY, "PRIVATE"), original.filters(), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", new IdentifierSearchSelector(ChangedNormalizer.VERSION, "PRIVATE"), original.filters(), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", original.selector(), IdentifierSearchFilters.of(List.of(), null), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", original.selector(), IdentifierSearchFilters.of(null, List.of()), 50),
                new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", original.selector(), original.filters(), 200))) {
            assertInvalid(changed, token);
        }
        var listed = new PartyIdentifierSearchScope(TENANT, new PartyId(new UUID(5, 5)),
                new PartyIdentifierSearchCriteria(null, null, null, null, 50));
        String other = new HmacPartyIdentifierCursorAdapter(ring).encode(listed, boundary(PartyIdentifierPageBoundary.Direction.NEXT));
        assertInvalid(original, other);
    }

    @Test
    void rejectsTamperedUnknownOversizedAndNoncanonicalTokens() {
        String token = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.NEXT));
        String[] parts = token.split("\\.");
        for (String invalid : List.of("", " ", "A".repeat(257), token + "=", token.substring(1),
                "2." + parts[1] + "." + parts[2] + "." + parts[3],
                "1.unknown." + parts[2] + "." + parts[3], "1.v1." + parts[2] + "=." + parts[3],
                "1.v1." + parts[2] + "." + "A".repeat(43))) {
            assertInvalid(scope(), invalid);
        }
    }

    @Test
    void authenticatedImpossibleFieldsRemainInvalid() throws GeneralSecurityException {
        String token = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.NEXT));
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[2]);
        for (int mutation = 0; mutation < 4; mutation++) {
            byte[] changed = payload.clone();
            switch (mutation) {
                case 0 -> changed[0] = 2;
                case 1 -> ByteBuffer.wrap(changed).putInt(9, -1);
                case 2 -> ByteBuffer.wrap(changed).putLong(1, Long.MAX_VALUE);
                case 3 -> ByteBuffer.wrap(changed).putInt(29, 0);
                default -> throw new AssertionError("Unexpected fixture mutation");
            }
            String signed = "1.v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(changed);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(ring.currentKey());
            mac.update("party-identifier-prefix-search-cursor-v1\0".getBytes(StandardCharsets.US_ASCII));
            String invalid = signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII)));
            assertInvalid(scope(), invalid);
        }
    }

    @Test
    void verifiesOldScopeWithItsSigningKeyRatherThanTheCurrentKey() {
        var boundary = boundary(PartyIdentifierPageBoundary.Direction.PREVIOUS);
        String old = adapter.encode(scope(), boundary);
        byte[] replacement = new byte[32];
        Arrays.fill(replacement, (byte) 7);
        var rotated = new HmacIdentifierSearchCursorAdapter(ring("v2", Map.of("v1", KEY,
                "v2", Base64.getEncoder().encodeToString(replacement))));
        assertEquals(boundary, rotated.decode(scope(), old));
        String current = rotated.encode(scope(), boundary);
        assertTrue(current.startsWith("1.v2."));
        assertEquals(boundary, rotated.decode(scope(), current));
        assertInvalid(scope(), current);
        assertNotEquals(old.split("\\.")[2], current.split("\\.")[2]);
    }

    @Test
    void maximumConfiguredKeyIdStillProducesBoundedTokensWithoutSelectorText() {
        String id = "K".repeat(64);
        var maximum = new HmacIdentifierSearchCursorAdapter(ring(id, Map.of(id, KEY)));
        var boundary = boundary(PartyIdentifierPageBoundary.Direction.NEXT);
        String token = maximum.encode(scope(), boundary);
        assertTrue(token.length() <= 256);
        assertEquals(boundary, maximum.decode(scope(), token));
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[2]);
        assertFalse(new String(payload, StandardCharsets.UTF_8).contains("PRIVATE"));
    }

    private void assertInvalid(IdentifierSearchScope scope, String token) {
        var failure = assertThrows(ApplicationException.class, () -> adapter.decode(scope, token));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierCursor.class, failure.failure());
        assertNull(failure.getCause());
    }

    private static IdentifierSearchScope scope() {
        return new IdentifierSearchScope(TENANT, SCHEME, "SEARCH", selector("PRIVATE"), IdentifierSearchFilters.of(null, null), 50);
    }

    private static IdentifierSearchSelector selector(String text) {
        var normalizer = StandardIdentifierNormalizer.TRIM_UPPERCASE_V1;
        return new IdentifierSearchSelector(normalizer, normalizer.normalize(text));
    }

    private static PartyIdentifierPageBoundary boundary(PartyIdentifierPageBoundary.Direction direction) {
        return new PartyIdentifierPageBoundary(direction, new PartyIdentifierPagePosition(Instant.ofEpochSecond(-1, 123456789),
                new PartyIdentifierId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))));
    }

    private static PartyCursorKeyMaterial ring(String id, Map<String, String> values) {
        return new PartyCursorKeyMaterial(new KeyConfiguration(id, values));
    }

    /** Supplies isolated signing-ring settings without CDI or deployment secrets. */
    private record KeyConfiguration(String id, Map<String, String> signingKeys) implements PartyPaginationConfiguration {
        @Override
        public Optional<String> currentSigningKeyId() {
            return Optional.of(id);
        }
    }

    /** Changes one normalization scope dimension at a time without introducing a production rule. */
    private enum ChangedNormalizer implements IdentifierNormalizer {
        KEY, VERSION;

        @Override
        public String key() {
            return this == KEY ? "OTHER_RULE" : "TRIM_UPPERCASE_V1";
        }

        @Override
        public IdentifierRuleVersion version() {
            return new IdentifierRuleVersion(this == VERSION ? 2 : 1);
        }

        @Override
        public String normalize(String completeValue) {
            return completeValue;
        }
    }
}
