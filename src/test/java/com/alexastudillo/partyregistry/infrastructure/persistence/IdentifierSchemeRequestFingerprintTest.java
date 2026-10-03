package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleRequest;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pins durable scheme digests and exact full-intent equivalence independently of HTTP ordering or attribution. */
class IdentifierSchemeRequestFingerprintTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final IdentifierSchemeId ID = new IdentifierSchemeId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    @Test
    void pinsVersionOneUtf8CreateAndEveryAction() {
        assertEquals("6ca410893697c418daa6536164db6e5c605bf3f031cbf04df84712f89554d44f", hash(input()));
        var golden = List.of("1ff27fcb80e7e7937cb82760a55ab2e9cb6c92a1eacfe499e37893e81ccd35bc",
                "8de8fc42eecb19089066385800cfac20de34df5f70669bcc23dc251590e33658",
                "c252868d0b842ea80ca255978bd021d9e013db7f5f13b9617583a6d5b425abb9");
        for (var action : IdentifierSchemeLifecycleAction.values()) {
            assertEquals(golden.get(action.ordinal()), IdentifierSchemeRequestFingerprint.fingerprint(TENANT,
                    new IdentifierSchemeLifecycleRequest(action, ID, new IdentifierSchemeVersion(7))));
        }
    }

    @Test
    void everyCreatePropertyAndTenantChangesTheDigestAndFullIntent() {
        var baseline = input();
        for (int field = 0; field < 11; field++) {
            var changed = changed(field);
            assertNotEquals(baseline, changed);
            assertNotEquals(hash(baseline), hash(changed), "field " + field);
        }
        assertNotEquals(hash(baseline), IdentifierSchemeRequestFingerprint.fingerprint(new TenantId(UUID.randomUUID()), baseline));
        assertNotEquals(hash(baseline), hash(new IdentifierSchemeCreateInput(baseline.code().strip(), "EC",
                baseline.category(), baseline.applicableSubjectType(), baseline.name(), null,
                baseline.normalizerKey(), baseline.validatorKey(), null, null, false)));
    }

    @Test
    void materializedNullableDefaultsAreEquivalentAndArbitraryIntegersRemainExact() {
        var baseline = input();
        var defaults = IdentifierSchemeCreateInput.withDefaults(baseline.code(), "EC", baseline.category(),
                baseline.applicableSubjectType(), baseline.name(), null, baseline.normalizerKey(), baseline.validatorKey(),
                null, null, null);
        assertEquals(baseline, defaults);
        assertEquals(hash(baseline), hash(defaults));
        var huge = BigInteger.ONE.shiftLeft(130);
        assertNotEquals(hash(withBounds(huge, null)), hash(withBounds(huge.add(BigInteger.ONE), null)));
        assertNotEquals(hash(withBounds(huge, null)), hash(withBounds(null, huge)));
        assertNotEquals(hash(withBounds(BigInteger.ZERO, null)), hash(withBounds(null, null)));
        assertNotEquals(hash(withBounds(huge.negate(), null)), hash(withBounds(huge, null)));
        assertEquals(hash(withBounds(new BigInteger("000123"), null)), hash(withBounds(BigInteger.valueOf(123), null)));
    }

    @Test
    void lifecycleSeparatesActionTargetVersionAndTenant() {
        var request = new IdentifierSchemeLifecycleRequest(IdentifierSchemeLifecycleAction.ACTIVATE, ID, new IdentifierSchemeVersion(7));
        String digest = IdentifierSchemeRequestFingerprint.fingerprint(TENANT, request);
        for (var action : List.of(IdentifierSchemeLifecycleAction.DEPRECATE, IdentifierSchemeLifecycleAction.RETIRE)) {
            assertNotEquals(digest, IdentifierSchemeRequestFingerprint.fingerprint(TENANT,
                    new IdentifierSchemeLifecycleRequest(action, ID, request.expectedVersion())));
        }
        assertNotEquals(digest, IdentifierSchemeRequestFingerprint.fingerprint(TENANT,
                new IdentifierSchemeLifecycleRequest(request.action(), new IdentifierSchemeId(UUID.randomUUID()), request.expectedVersion())));
        assertNotEquals(digest, IdentifierSchemeRequestFingerprint.fingerprint(TENANT,
                new IdentifierSchemeLifecycleRequest(request.action(), ID, new IdentifierSchemeVersion(8))));
        assertNotEquals(digest, IdentifierSchemeRequestFingerprint.fingerprint(new TenantId(UUID.randomUUID()), request));
        assertNotEquals(digest, hash(input()));
    }

    @Test
    void lengthDelimitersSeparateAdjacentTextAndNullFromEmpty() {
        var first = new IdentifierSchemeCreateInput("ab", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "c", "", "a:b", "c", null, null, false);
        var second = new IdentifierSchemeCreateInput("ab", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "c", "", "a", "b:c", null, null, false);
        assertNotEquals(hash(first), hash(second));
        assertNotEquals(hash(input()), hash(changed(5)));
    }

    @Test
    void onlyCanonicalMatchingHashesAreAccepted() {
        String digest = hash(input());
        assertTrue(IdentifierSchemeRequestFingerprint.matches(digest, digest));
        for (String invalid : List.of("", digest.toUpperCase(), " " + digest, "0".repeat(64), digest.substring(1))) {
            assertFalse(IdentifierSchemeRequestFingerprint.matches(invalid, digest));
        }
        assertFalse(IdentifierSchemeRequestFingerprint.matches(null, digest));
        assertFalse(IdentifierSchemeRequestFingerprint.matches(digest, null));
    }

    private static String hash(IdentifierSchemeCreateInput input) {
        return IdentifierSchemeRequestFingerprint.fingerprint(TENANT, input);
    }

    private static IdentifierSchemeCreateInput input() {
        return new IdentifierSchemeCreateInput(" Ab😀 ", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Exact name", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false);
    }

    private static IdentifierSchemeCreateInput changed(int field) {
        var base = input();
        return new IdentifierSchemeCreateInput(field == 0 ? " ab😀 " : base.code(), field == 1 ? "US" : "EC",
                field == 2 ? IdentifierCategory.PASSPORT : base.category(),
                field == 3 ? IdentifierSubjectType.NATURAL_PERSON : base.applicableSubjectType(),
                field == 4 ? "exact name" : base.name(), field == 5 ? "" : null,
                field == 6 ? "trim_uppercase_v1" : base.normalizerKey(),
                field == 7 ? "alphanumeric_v1" : base.validatorKey(),
                field == 8 ? BigInteger.ONE : null, field == 9 ? BigInteger.TEN : null, field == 10);
    }

    private static IdentifierSchemeCreateInput withBounds(BigInteger min, BigInteger max) {
        var base = input();
        return new IdentifierSchemeCreateInput(base.code(), "EC", base.category(), base.applicableSubjectType(),
                base.name(), null, base.normalizerKey(), base.validatorKey(), min, max, false);
    }
}
