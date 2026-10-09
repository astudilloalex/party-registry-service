package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.command.ChangePartyIdentifierLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins bounded exact identifier HMAC identities, independent scopes, raw code units and constant-length digest comparison. */
class HmacPartyIdentifierFingerprintAdapterTest {

    private static final TenantId TENANT = PartyIdentifierFixtures.TENANT;
    private static final PartyId PARTY = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final PartyIdentifierId IDENTIFIER = new PartyIdentifierId(UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744"));
    private static final LocalDate ISSUED = LocalDate.of(2026, Month.JUNE, 1);
    private static final LocalDate EXPIRES = LocalDate.of(2026, Month.JULY, 1);
    private final HmacPartyIdentifierFingerprintAdapter adapter = new HmacPartyIdentifierFingerprintAdapter(key((byte) 0x5a));

    @Test
    void matchesIndependentByteExactCreationAndWithdrawalVectors() {
        var create = new PartyIdentifierCreationRequest(PARTY, "scheme", "ab\0\ud800", " ", ISSUED, EXPIRES, true);
        var revoke = new PartyIdentifierActionRequest(PartyIdentifierOperation.REVOKE, PARTY, IDENTIFIER,
                new PartyIdentifierVersion(7), new PartyIdentifierReason("code\0", "\ud800\n"), false);
        assertEquals("e20fa9df77784d5b3136ea5e203427d5f693d0d8c83fcedc540c3cd737b43dbb", adapter.fingerprint(TENANT, create));
        assertEquals("30b06a74f3f6c4628d1c7983de45ad78c3f5566e7efd9478778aea36372c1a7a", adapter.fingerprint(TENANT, revoke));
    }

    @Test
    void everyCreationPropertyAndRawNormalizationDifferenceParticipates() {
        var original = creation("abc", "issuer");
        String digest = adapter.fingerprint(TENANT, original);
        for (var changed : List.of(
                new PartyIdentifierCreationRequest(new PartyId(UUID.randomUUID()), "scheme", "abc", "issuer", ISSUED, EXPIRES, false),
                new PartyIdentifierCreationRequest(PARTY, "SCHEME", "abc", "issuer", ISSUED, EXPIRES, false),
                creation("ABC", "issuer"), creation(" abc ", "issuer"), creation("abc", "ISSUER"),
                creation("abc", null), creation("abc", ""),
                new PartyIdentifierCreationRequest(PARTY, "scheme", "abc", "issuer", ISSUED.plusDays(1), EXPIRES, false),
                new PartyIdentifierCreationRequest(PARTY, "scheme", "abc", "issuer", ISSUED, EXPIRES.plusDays(1), false),
                new PartyIdentifierCreationRequest(PARTY, "scheme", "abc", "issuer", ISSUED, EXPIRES, true))) {
            assertNotEquals(digest, adapter.fingerprint(TENANT, changed));
        }
        assertNotEquals(adapter.fingerprint(TENANT, creation("abc", null)), adapter.fingerprint(TENANT, creation("abc", " ")));
    }

    @Test
    void creationAndActionAttributionOrRetryKeysAreNotEffectiveInput() {
        var input = new InitialPartyIdentifierInput("scheme", "abc", null, null, null, false);
        var first = new RegisterPartyIdentifierCommand(new RequestMetadata(TENANT, "first-user", UUID.randomUUID()), "first-key", PARTY, input);
        var retry = new RegisterPartyIdentifierCommand(new RequestMetadata(TENANT, "other-user", UUID.randomUUID()), "other-key", PARTY, input);
        assertEquals(adapter.fingerprint(TENANT, first.effectiveRequest()), adapter.fingerprint(TENANT, retry.effectiveRequest()));
        var action = new ChangePartyIdentifierLifecycleCommand(first.requestMetadata(), PARTY, IDENTIFIER,
                new PartyIdentifierVersion(7), PartyIdentifierOperation.REJECT, "key", PartyIdentifierReason.none());
        var other = new ChangePartyIdentifierLifecycleCommand(retry.requestMetadata(), PARTY, IDENTIFIER,
                new PartyIdentifierVersion(7), PartyIdentifierOperation.REJECT, null, new PartyIdentifierReason(null, null));
        assertEquals(adapter.fingerprint(TENANT, action.effectiveRequest()), adapter.fingerprint(TENANT, other.effectiveRequest()));
    }

    @Test
    void actionTargetsVersionsAndReplacementChoiceRemainIndependent() {
        var original = action(PartyIdentifierOperation.SET_PRIMARY, PartyIdentifierReason.none(), false);
        String digest = adapter.fingerprint(TENANT, original);
        for (var changed : List.of(
                new PartyIdentifierActionRequest(original.operation(), new PartyId(UUID.randomUUID()), IDENTIFIER,
                        original.expectedVersion(), original.reason(), false),
                new PartyIdentifierActionRequest(original.operation(), PARTY, new PartyIdentifierId(UUID.randomUUID()),
                        original.expectedVersion(), original.reason(), false),
                new PartyIdentifierActionRequest(original.operation(), PARTY, IDENTIFIER, new PartyIdentifierVersion(8), original.reason(), false),
                action(PartyIdentifierOperation.SET_PRIMARY, PartyIdentifierReason.none(), true))) {
            assertNotEquals(digest, adapter.fingerprint(TENANT, changed));
        }
    }

    @Test
    void exactAbsentEmptyWhitespaceControlsAndSurrogateReasonsDoNotCollide() {
        var absent = action(PartyIdentifierOperation.REJECT, PartyIdentifierReason.none(), false);
        String digest = adapter.fingerprint(TENANT, absent);
        for (var reason : List.of(new PartyIdentifierReason("", null), new PartyIdentifierReason(null, ""),
                new PartyIdentifierReason(" ", null), new PartyIdentifierReason("\0", "line\n"),
                new PartyIdentifierReason("\ud800", "\ud800"), new PartyIdentifierReason("\ud801", "\ud801"))) {
            assertNotEquals(digest, adapter.fingerprint(TENANT, action(PartyIdentifierOperation.REJECT, reason, false)));
        }
        String first = adapter.fingerprint(TENANT, action(PartyIdentifierOperation.REJECT, new PartyIdentifierReason("\ud800", null), false));
        String second = adapter.fingerprint(TENANT, action(PartyIdentifierOperation.REJECT, new PartyIdentifierReason("\ud801", null), false));
        assertNotEquals(first, second);
        assertNotEquals(adapter.fingerprint(TENANT, creation("\ud800", null)), adapter.fingerprint(TENANT, creation("\ud801", null)));
    }

    @Test
    void tenantsActionsAndSigningMaterialAreDomainSeparated() {
        var create = creation("abc", null);
        String digest = adapter.fingerprint(TENANT, create);
        assertNotEquals(digest, adapter.fingerprint(new TenantId(UUID.randomUUID()), create));
        var differentKey = new HmacPartyIdentifierFingerprintAdapter(key((byte) 0x6b));
        assertNotEquals(digest, differentKey.fingerprint(TENANT, create));
        var verify = action(PartyIdentifierOperation.VERIFY, PartyIdentifierReason.none(), false);
        var reject = action(PartyIdentifierOperation.REJECT, PartyIdentifierReason.none(), false);
        var revoke = action(PartyIdentifierOperation.REVOKE, PartyIdentifierReason.none(), false);
        assertNotEquals(adapter.fingerprint(TENANT, verify), adapter.fingerprint(TENANT, reject));
        assertNotEquals(adapter.fingerprint(TENANT, reject), adapter.fingerprint(TENANT, revoke));
    }

    @Test
    void exactMaximumUnitAndCodePointInputsRemainBounded() {
        var maximum = new PartyIdentifierCreationRequest(PARTY, "s".repeat(64), "v".repeat(256), "😀".repeat(64), null, null, false);
        assertEquals(64, adapter.fingerprint(TENANT, maximum).length());
        var reasons = action(PartyIdentifierOperation.REVOKE, new PartyIdentifierReason("😀".repeat(64), "😀".repeat(1000)), false);
        assertEquals(64, adapter.fingerprint(TENANT, reasons).length());
        var schemeTooLong = new PartyIdentifierCreationRequest(PARTY, "s".repeat(65), "abc", null, null, null, false);
        var valueTooLong = creation("v".repeat(257), null);
        var issuerTooLong = creation("abc", "x".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> adapter.fingerprint(TENANT, schemeTooLong));
        assertThrows(IllegalArgumentException.class, () -> adapter.fingerprint(TENANT, valueTooLong));
        assertThrows(IllegalArgumentException.class, () -> adapter.fingerprint(TENANT, issuerTooLong));
    }

    @Test
    void comparesFixedLengthDecodedHexAndRejectsUnsupportedShapes() {
        String digest = adapter.fingerprint(TENANT, creation("abc", null));
        assertTrue(adapter.matches(digest, digest));
        assertTrue(adapter.matches(digest, digest.toUpperCase(Locale.ROOT)));
        assertFalse(adapter.matches(digest, adapter.fingerprint(TENANT, creation("other", null))));
        for (String invalid : List.of("", "a".repeat(63), "a".repeat(65), "g".repeat(64), "sensitive-digest")) {
            assertFalse(adapter.matches(invalid, digest));
            assertFalse(adapter.matches(digest, invalid));
            assertFalse(adapter.matches(invalid, invalid));
        }
    }

    private static PartyIdentifierCreationRequest creation(String value, @Nullable String issuer) {
        return new PartyIdentifierCreationRequest(PARTY, "scheme", value, issuer, ISSUED, EXPIRES, false);
    }

    private static PartyIdentifierActionRequest action(PartyIdentifierOperation operation, PartyIdentifierReason reason, boolean replace) {
        return new PartyIdentifierActionRequest(operation, PARTY, IDENTIFIER, new PartyIdentifierVersion(7), reason, replace);
    }

    private static SecretKeySpec key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
