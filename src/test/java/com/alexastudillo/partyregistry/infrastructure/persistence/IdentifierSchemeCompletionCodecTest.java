package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises historical scheme replay and strict corruption classification
 * independently of API or ORM serialization.
 */
class IdentifierSchemeCompletionCodecTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final IdentifierSchemeId ID = new IdentifierSchemeId(
            UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final Instant ACCEPTED = CREATED.plusSeconds(50);
    private final IdentifierSchemeCompletionCodec codec = new IdentifierSchemeCompletionCodec();

    @Test
    void roundTripsExplicitCreationIntentAndOriginalDraftAuditAcrossJsonStorage() throws Exception {
        var completed = creation();
        var snapshot = codec.encode(TENANT, completed);
        assertEquals(1, snapshot.schemaVersion());
        assertEquals(5, snapshot.payload().size());
        assertEquals(11, snapshot.payload().get("request").size());
        assertEquals(18, snapshot.payload().get("scheme").size());
        assertTrue(snapshot.payload().get("request").get("minimumLength").isIntegralNumber());
        var serialized = new ObjectMapper().readTree(snapshot.payload().toString());
        assertEquals(completed, decode(completed, new IdempotencyResultSnapshot((short) 1, serialized)));
        assertEquals(completed,
                codec.decode(recordEntity(completed, snapshot, TENANT, completed.request().operation(), ID,
                        hash(completed), CREATED, "original-author")));
        assertFalse(serialized.has("data"));
        assertFalse(serialized.has("processId"));
        assertFalse(serialized.has("idempotencyKey"));
    }

    @ParameterizedTest
    @EnumSource(IdentifierSchemeLifecycleAction.class)
    void preservesEveryHistoricalActionWithObsoleteKeysAndMaximumAcceptedVersion(
            IdentifierSchemeLifecycleAction action) {
        var completed = lifecycle(action, Long.MAX_VALUE - 1);
        var snapshot = codec.encode(TENANT, completed);
        assertEquals(completed, decode(completed, snapshot));
        assertEquals("OBSOLETE_NORMALIZER_V0", decode(completed, snapshot).result().normalizerKey());
        assertEquals(Long.MAX_VALUE, decode(completed, snapshot).result().version().value());
        assertEquals("original-author", decode(completed, snapshot).result().createdBy());
        assertEquals("accepted-actor", decode(completed, snapshot).result().updatedBy());
        assertEquals(ACCEPTED, decode(completed, snapshot).result().updatedAt());
    }

    @Test
    void nullableValuesAndFalseDefaultsRoundTripWithoutAliasingTrees() {
        var result = IdentifierSchemeResult
                .fromAggregate(IdentifierScheme.create(ID, "Exact", "EC", IdentifierCategory.OTHER,
                        IdentifierSubjectType.BOTH, "Name", null, "OLD_NORMALIZER", "OLD_VALIDATOR", null, null, false,
                        CREATED, "original-author"));
        var completed = new CompletedIdentifierSchemeOperation(input(result), result);
        var snapshot = codec.encode(TENANT, completed);
        assertTrue(snapshot.payload().get("request").get("description").isNull());
        assertFalse(snapshot.payload().get("request").get("requiresExpiration").booleanValue());
        var entity = recordEntity(completed, snapshot, TENANT, completed.request().operation(), ID, hash(completed),
                CREATED, "original-author");
        ((ObjectNode) snapshot.payload().get("scheme")).put("name", "tampered");
        assertEquals(completed, codec.decode(entity));
        var exposed = entity.resultSnapshot();
        ((ObjectNode) exposed.get("request")).put("code", "tampered");
        assertEquals(completed, codec.decode(entity));
        var decoded = codec.decode(entity);
        ((ObjectNode) exposed.get("scheme")).put("updatedBy", "tampered");
        assertEquals(completed, decoded);
        assertEquals(completed, decode(completed, codec.encode(TENANT, completed)));
    }

    @Test
    void validatesStoredTenantOperationTargetHashVersionAndAcceptanceAttribution() {
        var completed = creation();
        var snapshot = codec.encode(TENANT, completed);
        assertPersistence(() -> codec.decode(recordEntity(completed, snapshot, new TenantId(UUID.randomUUID()),
                completed.request().operation(), ID, hash(completed), CREATED, "original-author")));
        assertPersistence(() -> codec.decode(recordEntity(completed, snapshot, TENANT, "party.create.v1", ID,
                hash(completed), CREATED, "original-author")));
        assertPersistence(() -> codec.decode(recordEntity(completed, snapshot, TENANT, completed.request().operation(),
                new IdentifierSchemeId(UUID.randomUUID()), hash(completed), CREATED, "original-author")));
        for (String hash : List.of("0".repeat(64), hash(completed).toUpperCase(), "invalid")) {
            assertPersistence(
                    () -> codec.decode(recordEntity(completed, snapshot, TENANT, completed.request().operation(), ID,
                            hash, CREATED, "original-author")));
        }
        assertPersistence(
                () -> codec.decode(recordEntity(completed, new IdempotencyResultSnapshot((short) 2, snapshot.payload()),
                        TENANT, completed.request().operation(), ID, hash(completed), CREATED, "original-author")));
        assertPersistence(
                () -> codec.decode(recordEntity(completed, snapshot, TENANT, completed.request().operation(), ID,
                        hash(completed), CREATED.plusNanos(1000), "original-author")));
        assertPersistence(
                () -> codec.decode(recordEntity(completed, snapshot, TENANT, completed.request().operation(), ID,
                        hash(completed), CREATED, "retry-actor")));
    }

    @Test
    void validDigestCannotReplaceFullCreationIntentConsistency() {
        var completed = creation();
        var original = (IdentifierSchemeCreateInput) completed.request();
        var changed = new IdentifierSchemeCreateInput(original.code(), original.issuingCountryCode(),
                original.category(),
                original.applicableSubjectType(), "Different valid name", original.description(),
                original.normalizerKey(),
                original.validatorKey(), original.minimumLength(), original.maximumLength(),
                original.requiresExpiration());
        var snapshot = codec.encode(TENANT, completed);
        object((ObjectNode) snapshot.payload(), "request").put("name", changed.name());
        String validChangedDigest = IdentifierSchemeRequestFingerprint.fingerprint(TENANT, changed);
        assertPersistence(() -> codec.decode(snapshot, TENANT, changed.operation(), ID, validChangedDigest, CREATED,
                "original-author"));
    }

    @ParameterizedTest
    @EnumSource(IdentifierSchemeLifecycleAction.class)
    void lifecycleAcceptanceUsesOriginalModificationAuditNotCreationOrRetryAttribution(
            IdentifierSchemeLifecycleAction action) {
        var completed = lifecycle(action, 7);
        var snapshot = codec.encode(TENANT, completed);
        assertEquals(completed, codec.decode(recordEntity(completed, snapshot, TENANT, action.operation(), ID,
                hash(completed), ACCEPTED, "accepted-actor")));
        assertPersistence(() -> codec.decode(recordEntity(completed, snapshot, TENANT, action.operation(), ID,
                hash(completed), CREATED, "original-author")));
        assertPersistence(() -> codec.decode(recordEntity(completed, snapshot, TENANT, action.operation(), ID,
                hash(completed), ACCEPTED, "retry-actor")));
    }

    @Test
    void requiresClosedObjectsAndEveryExplicitFieldWithStrictTokenTypes() {
        var completed = creation();
        for (String section : List.of("", "request", "scheme")) {
            var original = (ObjectNode) codec.encode(TENANT, completed).payload();
            JsonNode node = section.isEmpty() ? original : original.get(section);
            for (String field : node.properties().stream().map(Map.Entry::getKey).toList()) {
                corrupt(completed, root -> object(root, section).remove(field));
                corrupt(completed, root -> object(root, section).putArray(field));
                if (!List.of("description", "minimumLength", "maximumLength").contains(field)) {
                    corrupt(completed, root -> object(root, section).putNull(field));
                }
            }
            corrupt(completed, root -> object(root, section).put("unknown", "value"));
        }
        corrupt(completed, root -> root.put("schemaVersion", "1"));
        corrupt(completed, root -> root.put("schemaVersion", 1.0));
        corrupt(completed, root -> root.put("schemaVersion", 0));
        corrupt(completed, root -> root.put("tenantId", "1-1-1-1-1"));
        corrupt(completed, root -> object(root, "scheme").put("id", "1-1-1-1-1"));
        corrupt(completed, root -> object(root, "request").put("requiresExpiration", "true"));
        corrupt(completed, root -> object(root, "scheme").put("minimumLength", 1.5));
        corrupt(completed, root -> object(root, "scheme").put("version", BigInteger.ONE.shiftLeft(80)));
    }

    @Test
    void rejectsEveryAlteredEffectiveCreatePropertyAndInconsistentResult() {
        var completed = creation();
        var snapshot = codec.encode(TENANT, completed);
        for (String field : snapshot.payload().get("request").properties().stream().map(Map.Entry::getKey).toList()) {
            corrupt(completed, root -> {
                var request = object(root, "request");
                var value = request.get(field);
                if (value.isBoolean())
                    request.put(field, !value.booleanValue());
                else if (value.isIntegralNumber())
                    request.put(field, value.bigIntegerValue().add(BigInteger.ONE));
                else
                    request.put(field, value.textValue() + "changed");
            });
        }
        for (String field : List.of("code", "issuingCountryCode", "category", "applicableSubjectType", "name",
                "description",
                "normalizerKey", "validatorKey", "minimumLength", "maximumLength", "requiresExpiration")) {
            corrupt(completed, root -> {
                var scheme = object(root, "scheme");
                var value = scheme.get(field);
                if (value.isBoolean())
                    scheme.put(field, !value.booleanValue());
                else if (value.isIntegralNumber())
                    scheme.put(field, value.intValue() + 1);
                else
                    scheme.put(field, value.textValue() + "changed");
            });
        }
        corrupt(completed, root -> object(root, "scheme").put("status", "ACTIVE"));
        corrupt(completed, root -> object(root, "scheme").put("version", 1));
        corrupt(completed, root -> object(root, "scheme").put("updatedAt", ACCEPTED.toString()));
        corrupt(completed, root -> object(root, "scheme").put("updatedBy", "accepted-actor"));
        corrupt(completed, root -> object(root, "request").put("minimumLength", BigInteger.ONE.shiftLeft(100)));
    }

    @ParameterizedTest
    @EnumSource(IdentifierSchemeLifecycleAction.class)
    void rejectsActionTargetVersionAndResultInconsistency(IdentifierSchemeLifecycleAction action) {
        var completed = lifecycle(action, 7);
        corrupt(completed, root -> object(root, "request").put("action", "UNKNOWN"));
        corrupt(completed, root -> object(root, "request").put("schemeId", UUID.randomUUID().toString()));
        corrupt(completed, root -> object(root, "request").put("expectedVersion", 8));
        corrupt(completed, root -> object(root, "scheme").put("version", 7));
        corrupt(completed, root -> object(root, "scheme").put("status", "DRAFT"));
        corrupt(completed, root -> object(root, "scheme").put("updatedAt", CREATED.toString()));
        corrupt(completed, root -> object(root, "request").put("expectedVersion", Long.MAX_VALUE));
        corrupt(completed, root -> root.put("operation", "identifier-scheme.create.v1"));
    }

    @Test
    void revalidatesFullDomainInvariantsForLifecycleResultsWithoutCurrentRuleAdmission() {
        var completed = lifecycle(IdentifierSchemeLifecycleAction.RETIRE, 7);
        for (String field : List.of("code", "name", "normalizerKey", "validatorKey")) {
            corrupt(completed, root -> object(root, "scheme").put(field, " "));
            corrupt(completed, root -> object(root, "scheme").put(field, "😀".repeat(501)));
        }
        corrupt(completed, root -> object(root, "scheme").put("issuingCountryCode", "ec"));
        corrupt(completed, root -> object(root, "scheme").put("category", "UNKNOWN"));
        corrupt(completed, root -> object(root, "scheme").put("applicableSubjectType", "UNKNOWN"));
        corrupt(completed, root -> object(root, "scheme").put("description", "😀".repeat(501)));
        for (int bound : List.of(-1, 0, 32768)) {
            corrupt(completed, root -> object(root, "scheme").put("minimumLength", bound));
            corrupt(completed, root -> object(root, "scheme").put("maximumLength", bound));
        }
        corrupt(completed, root -> object(root, "scheme").put("maximumLength", 1));
        for (String actor : List.of("", "x".repeat(129), "unsafe\nactor")) {
            corrupt(completed, root -> object(root, "scheme").put("createdBy", actor));
            corrupt(completed, root -> object(root, "scheme").put("updatedBy", actor));
        }
        corrupt(completed, root -> object(root, "scheme").put("createdAt", ACCEPTED.plusSeconds(1).toString()));
        corrupt(completed, root -> object(root, "scheme").put("updatedAt", "not-an-instant"));
    }

    @Test
    void encodeRejectsNonnullButInconsistentCompletionsAndInvalidDomainResults() {
        var create = creation();
        var life = lifecycle(IdentifierSchemeLifecycleAction.ACTIVATE, 7);
        assertPersistence(
                () -> codec.encode(TENANT, new CompletedIdentifierSchemeOperation(create.request(), life.result())));
        assertPersistence(
                () -> codec.encode(TENANT, new CompletedIdentifierSchemeOperation(life.request(), create.result())));
        var invalid = new IdentifierSchemeResult(ID, "", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Name", null, "OLD", "OLD", null, null, false, IdentifierSchemeStatus.RETIRED,
                new IdentifierSchemeVersion(8), CREATED, "original-author", ACCEPTED, "accepted-actor");
        assertPersistence(() -> codec.encode(TENANT, new CompletedIdentifierSchemeOperation(
                lifecycle(IdentifierSchemeLifecycleAction.RETIRE, 7).request(), invalid)));
    }

    private void corrupt(CompletedIdentifierSchemeOperation completed, Consumer<ObjectNode> mutation) {
        var snapshot = codec.encode(TENANT, completed);
        mutation.accept((ObjectNode) snapshot.payload());
        assertPersistence(() -> decode(completed, snapshot));
    }

    private CompletedIdentifierSchemeOperation decode(CompletedIdentifierSchemeOperation completed,
            IdempotencyResultSnapshot snapshot) {
        return codec.decode(snapshot, TENANT, completed.request().operation(), ID, hash(completed),
                completed.result().updatedAt(), completed.result().updatedBy());
    }

    private static void assertPersistence(Runnable operation) {
        var failure = assertThrows(ApplicationException.class, operation::run);
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, failure.failure());
        assertEquals("Persistence operation failed", failure.getMessage());
        assertFalse(failure.getMessage().contains("changed"));
    }

    private static ObjectNode object(ObjectNode root, String section) {
        return section.isEmpty() ? root : (ObjectNode) root.get(section);
    }

    private static String hash(CompletedIdentifierSchemeOperation completed) {
        return IdentifierSchemeRequestFingerprint.fingerprint(TENANT, completed.request());
    }

    private static IdentifierSchemeIdempotencyRecordEntity recordEntity(CompletedIdentifierSchemeOperation completed,
            IdempotencyResultSnapshot snapshot, TenantId tenant, String operation, IdentifierSchemeId target,
            String hash, Instant acceptedAt, String acceptedBy) {
        assertNotNull(completed);
        return new IdentifierSchemeIdempotencyRecordEntity(
                new IdentifierSchemeIdempotencyRecordId(tenant.value(), operation, " Exact Key "),
                hash, target.value(), snapshot.schemaVersion(), snapshot.payload(), acceptedAt, acceptedBy);
    }

    private static CompletedIdentifierSchemeOperation creation() {
        var result = IdentifierSchemeResult
                .fromAggregate(IdentifierScheme.create(ID, " Exact😀 ", "EC", IdentifierCategory.OTHER,
                        IdentifierSubjectType.BOTH, " Exact Name ", "Description😀", "OBSOLETE_NORMALIZER_V0",
                        "OBSOLETE_VALIDATOR_V0",
                        2, 32767, true, CREATED, "original-author"));
        return new CompletedIdentifierSchemeOperation(input(result), result);
    }

    private static IdentifierSchemeCreateInput input(IdentifierSchemeResult result) {
        return new IdentifierSchemeCreateInput(result.code(), result.issuingCountryCode(), result.category(),
                result.applicableSubjectType(),
                result.name(), result.description(), result.normalizerKey(), result.validatorKey(),
                result.minimumLength() == null ? null : BigInteger.valueOf(result.minimumLength()),
                result.maximumLength() == null ? null : BigInteger.valueOf(result.maximumLength()),
                result.requiresExpiration());
    }

    private static CompletedIdentifierSchemeOperation lifecycle(IdentifierSchemeLifecycleAction action, long expected) {
        var base = creation().result();
        var status = switch (action) {
            case ACTIVATE -> IdentifierSchemeStatus.ACTIVE;
            case DEPRECATE -> IdentifierSchemeStatus.DEPRECATED;
            case RETIRE -> IdentifierSchemeStatus.RETIRED;
        };
        var result = new IdentifierSchemeResult(ID, base.code(), "EC", base.category(), base.applicableSubjectType(),
                base.name(), base.description(), base.normalizerKey(), base.validatorKey(), 2, 32767, true, status,
                new IdentifierSchemeVersion(expected + 1), CREATED, "original-author", ACCEPTED, "accepted-actor");
        return new CompletedIdentifierSchemeOperation(new IdentifierSchemeLifecycleRequest(action, ID,
                new IdentifierSchemeVersion(expected)), result);
    }
}
