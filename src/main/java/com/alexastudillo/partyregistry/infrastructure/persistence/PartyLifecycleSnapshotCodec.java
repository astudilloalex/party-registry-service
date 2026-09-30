package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.UUID;

/**
 * Stores and validates schema-three historical lifecycle results separately from registration snapshots and HTTP envelopes.
 */
@ApplicationScoped
public class PartyLifecycleSnapshotCodec {

    static final short SCHEMA_VERSION = 3;
    private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String REQUEST_FIELD = "request";
    private static final String PARTY_FIELD = "party";
    private static final String ACTION_FIELD = "action";
    private static final String PARTY_ID = "partyId";
    private static final String TENANT_ID = "tenantId";
    private static final String EXPECTED_VERSION = "expectedVersion";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_FIELD, REQUEST_FIELD, PARTY_FIELD);
    private static final Set<String> REQUEST_FIELDS = Set.of(ACTION_FIELD, PARTY_ID, EXPECTED_VERSION);
    private static final Set<String> PARTY_FIELDS = Set.of(PARTY_ID, TENANT_ID, "type", "displayName", "recordStatus",
            "version", "createdAt", "createdBy", "updatedAt", "updatedBy", "details");
    private static final Set<String> NATURAL_FIELDS = Set.of("givenNames", "familyNames", "preferredName", "birthDate",
            "dateOfDeath", "birthCountryCode");
    private static final Set<String> LEGAL_FIELDS = Set.of("legalName", "tradeName", "legalFormCode",
            "incorporationCountryCode", "incorporatedOn", "dissolvedOn");

    IdempotencyResultSnapshot encode(CompletedPartyLifecycle completed) {
        try {
            ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.put(SCHEMA_VERSION_FIELD, SCHEMA_VERSION);
            ObjectNode request = payload.putObject(REQUEST_FIELD);
            request.put(ACTION_FIELD, completed.request().action().name());
            request.put(PARTY_ID, completed.request().partyId().value().toString());
            request.put(EXPECTED_VERSION, completed.request().expectedVersion().value());
            payload.set(PARTY_FIELD, IdempotencyResultSnapshotCodec.encodeParty(restoredResult(completed.result())));
            return new IdempotencyResultSnapshot(SCHEMA_VERSION, payload);
        } catch (RuntimeException _) {
            throw invalidSnapshot();
        }
    }

    /** Validates stored metadata and hash against the saved request, never against the new retry's target or version. */
    CompletedPartyLifecycle decode(IdempotencyResultSnapshot snapshot, TenantId storedTenant,
            PartyLifecycleAction storedAction, PartyId storedPartyId, String storedHash) {
        try {
            JsonNode payload = snapshot.payload();
            requireFields(payload, ROOT_FIELDS);
            if (snapshot.schemaVersion() != SCHEMA_VERSION || requiredLong(payload, SCHEMA_VERSION_FIELD) != SCHEMA_VERSION) {
                throw invalidSnapshot();
            }
            JsonNode requestNode = payload.get(REQUEST_FIELD);
            requireFields(requestNode, REQUEST_FIELDS);
            var request = new PartyLifecycleRequest(PartyLifecycleAction.valueOf(requiredText(requestNode, ACTION_FIELD)),
                    new PartyId(requiredUuid(requestNode, PARTY_ID)), new PartyVersion(requiredLong(requestNode, EXPECTED_VERSION)));
            JsonNode partyNode = payload.get(PARTY_FIELD);
            requireFields(partyNode, PARTY_FIELDS);
            PartyType type = PartyType.valueOf(requiredText(partyNode, "type"));
            requireFields(partyNode.get("details"), type == PartyType.NATURAL_PERSON ? NATURAL_FIELDS : LEGAL_FIELDS);
            requiredUuid(partyNode, PARTY_ID);
            requiredUuid(partyNode, TENANT_ID);
            PartyDetailsResult result = restoredResult(IdempotencyResultSnapshotCodec.decodeParty(partyNode));
            if (request.action() != storedAction || !request.partyId().equals(storedPartyId)
                    || !result.tenantId().equals(storedTenant)
                    || !PartyLifecycleFingerprint.matches(storedHash, PartyLifecycleFingerprint.fingerprint(storedTenant, request))) {
                throw invalidSnapshot();
            }
            return new CompletedPartyLifecycle(request, result);
        } catch (RuntimeException _) {
            throw invalidSnapshot();
        }
    }

    private static PartyDetailsResult restoredResult(PartyDetailsResult result) {
        var audit = new AuditInfo(result.createdAt(), result.createdBy(), result.updatedAt(), result.updatedBy());
        Party restored = switch (result) {
            case NaturalPersonResult natural -> NaturalPerson.restore(natural.partyId(), natural.tenantId(), natural.displayName(),
                    natural.recordStatus(), natural.version(), audit, new NaturalPersonDetails(natural.givenNames(),
                            natural.familyNames(), natural.preferredName(), natural.birthDate(), natural.dateOfDeath(), natural.birthCountryCode()));
            case LegalEntityResult legal -> LegalEntity.restore(legal.partyId(), legal.tenantId(), legal.displayName(),
                    legal.recordStatus(), legal.version(), audit, new LegalEntityDetails(legal.legalName(), legal.tradeName(),
                            legal.legalFormCode(), legal.incorporationCountryCode(), legal.incorporatedOn(), legal.dissolvedOn()));
        };
        return PartyDetailsResult.fromAggregate(restored);
    }

    private static void requireFields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject() || node.size() != allowed.size()
                || !node.properties().stream().allMatch(entry -> allowed.contains(entry.getKey()))) {
            throw invalidSnapshot();
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalidSnapshot();
        }
        return value.textValue();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidSnapshot();
        }
        return value.longValue();
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String value = requiredText(node, field);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) {
            throw invalidSnapshot();
        }
        return id;
    }

    private static ApplicationException invalidSnapshot() {
        return new ApplicationException(new ApplicationFailure.PersistenceFailure(),
                new IllegalStateException("Stored lifecycle snapshot is invalid"));
    }
}
