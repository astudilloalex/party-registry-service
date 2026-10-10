package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPagePosition;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies authenticated identifier boundaries, exact filter scope, malformed tokens and retained-key rotation. */
class HmacPartyIdentifierCursorAdapterTest {

    private static final String KEY = "Y3Vyc29yLWtleS12MS0wMTIzNDU2Nzg5YWJjZGVmMDE=";
    private static final Instant CREATED = LocalDate.of(2026, Month.OCTOBER, 4)
            .atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000);
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());
    private static final IdentifierSchemeId SCHEME = new IdentifierSchemeId(UUID.randomUUID());
    private final PartyCursorKeyMaterial ring = ring("v1", Map.of("v1", KEY));
    private final HmacPartyIdentifierCursorAdapter adapter = new HmacPartyIdentifierCursorAdapter(ring);

    @Test
    void roundTripsBothDirectionsFullPrecisionAndEffectiveCapacity() {
        for (var direction : PartyIdentifierPageBoundary.Direction.values()) {
            for (int limit : new int[]{1, 50, 200}) {
                PartyIdentifierSearchScope scope = scope(new PartyIdentifierSearchCriteria(null, null, null, null, limit));
                PartyIdentifierPageBoundary boundary = boundary(direction, CREATED);
                String token = adapter.encode(scope, boundary);
                assertEquals(boundary, adapter.decode(scope, token));
                assertTrue(token.length() <= 256);
            }
        }
        Instant beforeEpoch = LocalDate.of(1960, Month.FEBRUARY, 29).atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(999999999);
        PartyIdentifierPageBoundary boundary = boundary(PartyIdentifierPageBoundary.Direction.NEXT, beforeEpoch);
        assertEquals(boundary, adapter.decode(scope(), adapter.encode(scope(), boundary)));
        assertEquals(adapter.encode(scope(), boundary), adapter.encode(scope(new PartyIdentifierSearchCriteria(
                SCHEME, "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, true, PartyIdentifierSearchCriteria.DEFAULT_LIMIT)), boundary));
    }

    @Test
    void rejectsEveryChangedScopeDimensionAndExactCase() {
        String token = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.NEXT, CREATED));
        var criteria = scope().criteria();
        List<PartyIdentifierSearchScope> alternatives = List.of(
                new PartyIdentifierSearchScope(new TenantId(UUID.randomUUID()), PARTY, criteria),
                new PartyIdentifierSearchScope(TENANT, new PartyId(UUID.randomUUID()), criteria),
                scope(new PartyIdentifierSearchCriteria(null, "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, true, 50)),
                scope(new PartyIdentifierSearchCriteria(new IdentifierSchemeId(UUID.randomUUID()), "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, true, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, null, PartyIdentifierStatus.VERIFIED, true, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, "ec_tax_id", PartyIdentifierStatus.VERIFIED, true, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, "EC_TAX_ID", null, true, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, "EC_TAX_ID", PartyIdentifierStatus.REVOKED, true, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, false, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, null, 50)),
                scope(new PartyIdentifierSearchCriteria(SCHEME, "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, true, 200)));
        alternatives.forEach(other -> assertInvalid(other, token));
        PartyIdentifierSearchScope absent = scope(new PartyIdentifierSearchCriteria(null, null, null, null, 50));
        String absentToken = adapter.encode(absent, boundary(PartyIdentifierPageBoundary.Direction.NEXT, CREATED));
        assertInvalid(scope(new PartyIdentifierSearchCriteria(null, null, null, false, 50)), absentToken);
    }

    @Test
    void keepsExactUnicodeCodeUnitsInScope() {
        PartyIdentifierSearchScope first = scope(new PartyIdentifierSearchCriteria(null, "\ud800", null, null, 50));
        String token = adapter.encode(first, boundary(PartyIdentifierPageBoundary.Direction.NEXT, CREATED));
        assertEquals(CREATED, adapter.decode(first, token).position().createdAt());
        assertInvalid(scope(new PartyIdentifierSearchCriteria(null, "\ud801", null, null, 50)), token);
        assertInvalid(scope(new PartyIdentifierSearchCriteria(null, "?", null, null, 50)), token);
    }

    @Test
    void rejectsNoncanonicalOversizedTamperedAndUnsupportedTokens() {
        String token = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.NEXT, CREATED));
        String[] parts = token.split("\\.");
        for (String invalid : new String[]{"", " ", "x".repeat(257), token + "=", token.substring(1),
                "2." + parts[1] + "." + parts[2] + "." + parts[3],
                "1.unknown." + parts[2] + "." + parts[3],
                "1.v1." + parts[2] + "=." + parts[3],
                "1.v1." + parts[2] + "." + "A".repeat(43)}) {
            assertInvalid(scope(), invalid);
        }
    }

    @Test
    void rejectsAuthenticatedImpossiblePayloadFields() throws GeneralSecurityException {
        String token = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.NEXT, CREATED));
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[2]);
        byte[] direction = payload.clone();
        direction[0] = 2;
        byte[] nanos = payload.clone();
        ByteBuffer.wrap(nanos).putInt(9, -1);
        byte[] timestamp = payload.clone();
        ByteBuffer.wrap(timestamp).putLong(1, Long.MAX_VALUE);
        byte[] limit = payload.clone();
        ByteBuffer.wrap(limit).putInt(29, 0);
        for (byte[] invalid : new byte[][]{direction, nanos, timestamp, limit}) {
            String signed = signPayload(invalid, "party-identifier-list-cursor-v1\0");
            assertInvalid(scope(), signed);
        }
        assertInvalid(scope(), signPayload(payload, "nationality-list-cursor-v1\0"));
    }

    @Test
    void rejectsOtherResourceTokensEvenWithSharedKeysAndMatchingTuple() {
        var nationalityAdapter = new HmacNationalityCursorAdapter(ring);
        var position = new NationalityPagePosition(CREATED, new NationalityId(UUID.randomUUID()));
        var nationalityScope = new NationalitySearchScope(TENANT, PARTY, new NationalitySearchCriteria(
                null, null, LocalDate.of(2026, Month.OCTOBER, 4), false, 50));
        String other = nationalityAdapter.encode(new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT, position), nationalityScope);
        assertInvalid(scope(), other);
        String token = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.NEXT, CREATED));
        ApplicationException failure = assertThrows(ApplicationException.class, () -> nationalityAdapter.decode(token, nationalityScope));
        assertInstanceOf(ApplicationFailure.InvalidNationalityCursor.class, failure.failure());
    }

    @Test
    void retainsOldVerificationKeysAndSignsWithCurrentRotationKey() {
        String old = adapter.encode(scope(), boundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, CREATED));
        String replacement = Base64.getEncoder().encodeToString("synthetic-rotation-key-012345678".getBytes(StandardCharsets.US_ASCII));
        var rotated = new HmacPartyIdentifierCursorAdapter(ring("v2", Map.of("v1", KEY, "v2", replacement)));
        PartyIdentifierPageBoundary boundary = adapter.decode(scope(), old);
        assertEquals(boundary, rotated.decode(scope(), old));
        String current = rotated.encode(scope(), boundary);
        assertTrue(current.startsWith("1.v2."));
        assertEquals(boundary, rotated.decode(scope(), current));
        assertInvalid(scope(), current);
    }

    private void assertInvalid(PartyIdentifierSearchScope scope, String token) {
        ApplicationException failure = assertThrows(ApplicationException.class, () -> adapter.decode(scope, token));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierCursor.class, failure.failure());
        assertEquals("Invalid identifier cursor", failure.getMessage());
        assertNull(failure.getCause());
    }

    private String signPayload(byte[] payload, String context) throws GeneralSecurityException {
        String signed = "1.v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(ring.currentKey());
        mac.update(context.getBytes(StandardCharsets.US_ASCII));
        byte[] signature = mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII));
        return signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private static PartyIdentifierPageBoundary boundary(PartyIdentifierPageBoundary.Direction direction, Instant instant) {
        return new PartyIdentifierPageBoundary(direction, new PartyIdentifierPagePosition(instant,
                new PartyIdentifierId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))));
    }

    private static PartyIdentifierSearchScope scope() {
        return scope(new PartyIdentifierSearchCriteria(SCHEME, "EC_TAX_ID", PartyIdentifierStatus.VERIFIED, true, 50));
    }

    private static PartyIdentifierSearchScope scope(PartyIdentifierSearchCriteria criteria) {
        return new PartyIdentifierSearchScope(TENANT, PARTY, criteria);
    }

    private static PartyCursorKeyMaterial ring(String current, Map<String, String> keys) {
        return new PartyCursorKeyMaterial(new TestConfiguration(Optional.of(current), keys));
    }

    /** Supplies isolated test-only cursor rotation material through the existing configuration contract. */
    private record TestConfiguration(Optional<String> currentSigningKeyId, Map<String, String> signingKeys)
            implements PartyPaginationConfiguration { }
}
