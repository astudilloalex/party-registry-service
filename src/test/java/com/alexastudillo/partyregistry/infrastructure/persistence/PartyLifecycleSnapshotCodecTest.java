package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies safe historical lifecycle snapshots, versioned request identity, corruption rejection, and registration compatibility.
 */
class PartyLifecycleSnapshotCodecTest {

    private static final PartyId PARTY_ID = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT_ID = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC);
    private final PartyLifecycleSnapshotCodec codec = new PartyLifecycleSnapshotCodec();

    @ParameterizedTest
    @EnumSource(PartyType.class)
    void preservesOriginalDataVersionAuditAndNullableFieldsForEveryAction(PartyType type) {
        for (PartyLifecycleAction action : PartyLifecycleAction.values()) {
            CompletedPartyLifecycle original = completed(type, action);
            IdempotencyResultSnapshot snapshot = codec.encode(original);
            String hash = PartyLifecycleFingerprint.fingerprint(TENANT_ID, original.request());

            assertEquals(3, snapshot.schemaVersion());
            assertEquals(3, snapshot.payload().path("schemaVersion").intValue());
            assertEquals(original, codec.decode(snapshot, TENANT_ID, action, PARTY_ID, hash));
            String json = snapshot.payload().toString();
            for (String forbidden : List.of("initialIdentifier", "identifiers", "encryptedValue", "normalizedValue",
                    "normalizedValueHash", "keyMaterial", "processId", "apiResponse", "status", "code", "data")) {
                assertFalse(json.contains('"' + forbidden + '"'), forbidden);
            }
            assertEquals("original-updater", original.result().updatedBy());
            assertEquals(5, original.result().version().value());
        }
    }

    @Test
    void rejectsSchemaIdentitySubtypeAuditAndUnexpectedFieldCorruptionWithoutLoggingPayloads() {
        CompletedPartyLifecycle original = completed(PartyType.NATURAL_PERSON, PartyLifecycleAction.ARCHIVE);
        IdempotencyResultSnapshot snapshot = codec.encode(original);
        String hash = PartyLifecycleFingerprint.fingerprint(TENANT_ID, original.request());
        List<Consumer<ObjectNode>> corruptions = List.of(
                node -> node.put("schemaVersion", 4),
                node -> node.put("schemaVersion", "3"),
                node -> node.put("encryptedValue", "sensitive-snapshot-value"),
                node -> node.withObject("/request").put("action", "ACTIVATE"),
                node -> node.withObject("/request").put("partyId", "sensitive-snapshot-value"),
                node -> node.withObject("/request").put("expectedVersion", "4"),
                node -> node.withObject("/party").put("version", 999),
                node -> node.withObject("/party").put("type", "LEGAL_ENTITY"),
                node -> node.withObject("/party").put("recordStatus", "INACTIVE"),
                node -> node.withObject("/party").put("tenantId", UUID.randomUUID().toString()),
                node -> node.withObject("/party").put("updatedAt", CREATED.minusSeconds(1).toString()),
                node -> node.withObject("/party/details").put("birthDate", "sensitive-snapshot-value"),
                node -> node.withObject("/party/details").put("preferredName", 123),
                node -> node.withObject("/party/details").put("identifierValue", "sensitive-snapshot-value"));
        for (Consumer<ObjectNode> corrupt : corruptions) {
            ObjectNode payload = (ObjectNode) snapshot.payload().deepCopy();
            corrupt.accept(payload);
            assertInvalid(new IdempotencyResultSnapshot((short) 3, payload), TENANT_ID, PartyLifecycleAction.ARCHIVE, PARTY_ID, hash);
        }
        assertInvalid(new IdempotencyResultSnapshot((short) 2, snapshot.payload()), TENANT_ID,
                PartyLifecycleAction.ARCHIVE, PARTY_ID, hash);
    }

    @Test
    void validatesSavedScopeAndHashRatherThanReconstructingCurrentPartyState() {
        CompletedPartyLifecycle original = completed(PartyType.LEGAL_ENTITY, PartyLifecycleAction.ARCHIVE);
        IdempotencyResultSnapshot snapshot = codec.encode(original);
        String hash = PartyLifecycleFingerprint.fingerprint(TENANT_ID, original.request());

        assertInvalid(snapshot, TENANT_ID, PartyLifecycleAction.ACTIVATE, PARTY_ID, hash);
        assertInvalid(snapshot, new TenantId(UUID.randomUUID()), PartyLifecycleAction.ARCHIVE, PARTY_ID, hash);
        assertInvalid(snapshot, TENANT_ID, PartyLifecycleAction.ARCHIVE, new PartyId(UUID.randomUUID()), hash);
        assertInvalid(snapshot, TENANT_ID, PartyLifecycleAction.ARCHIVE, PARTY_ID, "0".repeat(64));
        assertEquals(original, codec.decode(snapshot, TENANT_ID, PartyLifecycleAction.ARCHIVE, PARTY_ID, hash));
    }

    @Test
    void rejectsAnInvalidResultBeforeCreatingAPersistableSnapshot() {
        CompletedPartyLifecycle valid = completed(PartyType.NATURAL_PERSON, PartyLifecycleAction.ARCHIVE);
        var source = assertInstanceOf(NaturalPersonResult.class, valid.result());
        var invalid = new NaturalPersonResult(source.partyId(), source.tenantId(), source.type(), " ",
                source.recordStatus(), source.version(), source.givenNames(), source.familyNames(), source.preferredName(),
                source.birthDate(), source.dateOfDeath(), source.birthCountryCode(), source.createdAt(), source.createdBy(),
                source.updatedAt(), source.updatedBy());
        var completed = new CompletedPartyLifecycle(valid.request(), invalid);
        var failure = assertThrows(ApplicationException.class, () -> codec.encode(completed));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, failure.failure());
    }

    @Test
    void pinsVersionedFingerprintEncodingAndSeparatesEachEffectiveInput() {
        var request = new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, PARTY_ID, new PartyVersion(4));
        String hash = PartyLifecycleFingerprint.fingerprint(TENANT_ID, request);
        assertEquals("c567480d62a1cd48c0413739d4d27011696b9befa4afc7d69d6cafde45cc7893", hash);
        assertNotEquals(hash, PartyLifecycleFingerprint.fingerprint(new TenantId(UUID.randomUUID()), request));
        assertNotEquals(hash, PartyLifecycleFingerprint.fingerprint(TENANT_ID,
                new PartyLifecycleRequest(PartyLifecycleAction.ACTIVATE, PARTY_ID, new PartyVersion(4))));
        assertNotEquals(hash, PartyLifecycleFingerprint.fingerprint(TENANT_ID,
                new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, new PartyId(UUID.randomUUID()), new PartyVersion(4))));
        assertNotEquals(hash, PartyLifecycleFingerprint.fingerprint(TENANT_ID,
                new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, PARTY_ID, new PartyVersion(5))));
    }

    @Test
    void leavesVersionOneAndTwoRegistrationReadersIndependentFromLifecycleSchema() {
        var registrationCodec = new IdempotencyResultSnapshotCodec();
        var party = assertInstanceOf(NaturalPersonResult.class,
                PartyDetailsResult.fromAggregate(original(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT)));
        IdempotencyResultSnapshot legacy = registrationCodec.encodeLegacyNaturalPerson(party);
        var identifier = new PartyIdentifierResult(new PartyIdentifierId(UUID.randomUUID()), PARTY_ID,
                new IdentifierSchemeId(UUID.randomUUID()), "TEST", "***12", PartyIdentifierStatus.PENDING_VERIFICATION,
                false, null, null, null, null, null, PartyIdentifierVersion.initial(), CREATED, CREATED);
        var registration = new PartyRegistrationResult(party, identifier, PartyRegistrationOutcome.CREATED);
        IdempotencyResultSnapshot versionTwo = registrationCodec.encodeRegistration(registration);
        assertEquals(party, registrationCodec.decodeLegacyNaturalPerson(legacy));
        assertEquals(registration, registrationCodec.decodeRegistration(versionTwo));
        assertEquals(1, legacy.schemaVersion());
        assertEquals(2, versionTwo.schemaVersion());
        assertInvalid(legacy, TENANT_ID, PartyLifecycleAction.ARCHIVE, PARTY_ID, "0".repeat(64));
        assertInvalid(versionTwo, TENANT_ID, PartyLifecycleAction.ARCHIVE, PARTY_ID, "0".repeat(64));
    }

    private void assertInvalid(IdempotencyResultSnapshot snapshot, TenantId tenant, PartyLifecycleAction action,
            PartyId partyId, String hash) {
        var failure = assertThrows(ApplicationException.class, () -> codec.decode(snapshot, tenant, action, partyId, hash));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, failure.failure());
        assertEquals("Persistence operation failed", failure.getMessage());
        var cause = assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("Stored lifecycle snapshot is invalid", cause.getMessage());
        assertNull(cause.getCause());
    }

    private static CompletedPartyLifecycle completed(PartyType type, PartyLifecycleAction action) {
        Party party = original(type, action == PartyLifecycleAction.DEACTIVATE ? PartyRecordStatus.ACTIVE : PartyRecordStatus.DRAFT);
        Instant occurredAt = CREATED.plusSeconds(2);
        Party accepted = switch (action) {
            case ACTIVATE -> party.activate(occurredAt, "original-updater");
            case DEACTIVATE -> party.deactivate(occurredAt, "original-updater");
            case ARCHIVE -> party.archive(occurredAt, "original-updater");
        };
        return new CompletedPartyLifecycle(new PartyLifecycleRequest(action, PARTY_ID, party.version()),
                PartyDetailsResult.fromAggregate(accepted));
    }

    private static Party original(PartyType type, PartyRecordStatus status) {
        var audit = AuditInfo.initial(CREATED, "original-creator");
        var version = new PartyVersion(4);
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(PARTY_ID, TENANT_ID, " Historical Label ", status, version, audit,
                    new NaturalPersonDetails(" Ada ", "Lovelace", null, LocalDate.of(1815, Month.DECEMBER, 10), null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(PARTY_ID, TENANT_ID, " Historical Company ", status, version, audit,
                    new LegalEntityDetails("Mixed Company", " Trade ", null, "GB", LocalDate.of(1843, Month.JANUARY, 1), null));
        };
    }
}
