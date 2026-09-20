package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Encodes and decodes explicit, versioned, transport-neutral idempotency snapshots.
 */
@ApplicationScoped
public class IdempotencyResultSnapshotCodec {

    static final short REGISTRATION_SCHEMA_VERSION = 2;

    private static final String BIRTH_COUNTRY_CODE_FIELD = "birthCountryCode";
    private static final String BIRTH_DATE_FIELD = "birthDate";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String CREATED_BY_FIELD = "createdBy";
    private static final String DATE_OF_DEATH_FIELD = "dateOfDeath";
    private static final String DISPLAY_NAME_FIELD = "displayName";
    private static final String FAMILY_NAMES_FIELD = "familyNames";
    private static final String GIVEN_NAMES_FIELD = "givenNames";
    private static final String PARTY_ID_FIELD = "partyId";
    private static final String PREFERRED_NAME_FIELD = "preferredName";
    private static final String RECORD_STATUS_FIELD = "recordStatus";
    private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String TYPE_FIELD = "type";
    private static final String UPDATED_AT_FIELD = "updatedAt";
    private static final String UPDATED_BY_FIELD = "updatedBy";
    private static final String VERSION_FIELD = "version";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * Encodes the retained identifier-free natural-person snapshot schema.
     *
     * @param result original legacy creation result
     * @return version-one snapshot payload and column version
     */
    IdempotencyResultSnapshot encodeLegacyNaturalPerson(NaturalPersonResult result) {
        try {
            NaturalPersonResultSnapshot snapshot = NaturalPersonResultSnapshot.from(result);
            ObjectNode payload = JSON.objectNode();
            payload.put(SCHEMA_VERSION_FIELD, snapshot.schemaVersion());
            payload.put(PARTY_ID_FIELD, snapshot.partyId().toString());
            payload.put(TENANT_ID_FIELD, snapshot.tenantId().toString());
            payload.put(TYPE_FIELD, snapshot.type());
            payload.put(DISPLAY_NAME_FIELD, snapshot.displayName());
            payload.put(RECORD_STATUS_FIELD, snapshot.recordStatus());
            payload.put(VERSION_FIELD, snapshot.version());
            payload.put(GIVEN_NAMES_FIELD, snapshot.givenNames());
            payload.put(FAMILY_NAMES_FIELD, snapshot.familyNames());
            putNullable(payload, PREFERRED_NAME_FIELD, snapshot.preferredName());
            putNullable(payload, BIRTH_DATE_FIELD, snapshot.birthDate());
            putNullable(payload, DATE_OF_DEATH_FIELD, snapshot.dateOfDeath());
            putNullable(payload, BIRTH_COUNTRY_CODE_FIELD, snapshot.birthCountryCode());
            payload.put(CREATED_AT_FIELD, snapshot.createdAt().toString());
            payload.put(CREATED_BY_FIELD, snapshot.createdBy());
            payload.put(UPDATED_AT_FIELD, snapshot.updatedAt().toString());
            payload.put(UPDATED_BY_FIELD, snapshot.updatedBy());
            return new IdempotencyResultSnapshot(snapshot.schemaVersion(), payload);
        } catch (ApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PersistenceExceptionTranslator.toApplicationException(exception);
        }
    }

    /**
     * Decodes only the retained version-one natural-person schema.
     *
     * @param snapshot stored column version and JSON payload
     * @return exact legacy creation result
     */
    NaturalPersonResult decodeLegacyNaturalPerson(IdempotencyResultSnapshot snapshot) {
        try {
            requireSchemaVersion(snapshot, NaturalPersonResultSnapshot.CURRENT_SCHEMA_VERSION);
            JsonNode payload = snapshot.payload();
            return new NaturalPersonResultSnapshot(
                    (short) requiredInt(payload, SCHEMA_VERSION_FIELD),
                    requiredUuid(payload, PARTY_ID_FIELD),
                    requiredUuid(payload, TENANT_ID_FIELD),
                    requiredText(payload, TYPE_FIELD),
                    requiredText(payload, DISPLAY_NAME_FIELD),
                    requiredText(payload, RECORD_STATUS_FIELD),
                    requiredLong(payload, VERSION_FIELD),
                    requiredText(payload, GIVEN_NAMES_FIELD),
                    requiredText(payload, FAMILY_NAMES_FIELD),
                    nullableText(payload, PREFERRED_NAME_FIELD),
                    nullableDate(payload, BIRTH_DATE_FIELD),
                    nullableDate(payload, DATE_OF_DEATH_FIELD),
                    nullableText(payload, BIRTH_COUNTRY_CODE_FIELD),
                    requiredInstant(payload, CREATED_AT_FIELD),
                    requiredText(payload, CREATED_BY_FIELD),
                    requiredInstant(payload, UPDATED_AT_FIELD),
                    requiredText(payload, UPDATED_BY_FIELD))
                    .toResult();
        } catch (ApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PersistenceExceptionTranslator.toApplicationException(exception);
        }
    }

    /**
     * Encodes the complete safe Party registration result as schema version two.
     *
     * @param result transport-neutral registration result
     * @return version-two snapshot payload and column version
     */
    IdempotencyResultSnapshot encodeRegistration(PartyRegistrationResult result) {
        try {
            ObjectNode payload = JSON.objectNode();
            payload.put(SCHEMA_VERSION_FIELD, REGISTRATION_SCHEMA_VERSION);
            payload.put("outcome", result.outcome().name());
            payload.set("party", encodeParty(result.party()));
            payload.set("initialIdentifier", encodeIdentifier(result.initialIdentifier()));
            return new IdempotencyResultSnapshot(REGISTRATION_SCHEMA_VERSION, payload);
        } catch (ApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PersistenceExceptionTranslator.toApplicationException(exception);
        }
    }

    /**
     * Decodes only schema version two and never reinterprets a legacy payload.
     *
     * @param snapshot stored column version and JSON payload
     * @return exact safe registration result
     */
    PartyRegistrationResult decodeRegistration(IdempotencyResultSnapshot snapshot) {
        try {
            requireSchemaVersion(snapshot, REGISTRATION_SCHEMA_VERSION);
            JsonNode payload = snapshot.payload();
            return new PartyRegistrationResult(
                    decodeParty(requiredObject(payload, "party")),
                    decodeIdentifier(requiredObject(payload, "initialIdentifier")),
                    PartyRegistrationOutcome.valueOf(requiredText(payload, "outcome")));
        } catch (ApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PersistenceExceptionTranslator.toApplicationException(exception);
        }
    }

    /** Shares only the safe Party representation across independently versioned snapshot envelopes. */
    static ObjectNode encodeParty(PartyDetailsResult party) {
        ObjectNode payload = JSON.objectNode();
        payload.put(PARTY_ID_FIELD, party.partyId().value().toString());
        payload.put(TENANT_ID_FIELD, party.tenantId().value().toString());
        payload.put(TYPE_FIELD, party.type().name());
        payload.put(DISPLAY_NAME_FIELD, party.displayName());
        payload.put(RECORD_STATUS_FIELD, party.recordStatus().name());
        payload.put(VERSION_FIELD, party.version().value());
        payload.put(CREATED_AT_FIELD, party.createdAt().toString());
        payload.put(CREATED_BY_FIELD, party.createdBy());
        payload.put(UPDATED_AT_FIELD, party.updatedAt().toString());
        payload.put(UPDATED_BY_FIELD, party.updatedBy());

        ObjectNode details = payload.putObject("details");
        switch (party) {
            case NaturalPersonResult naturalPerson -> {
                requirePartyType(naturalPerson.type(), PartyType.NATURAL_PERSON);
                details.put(GIVEN_NAMES_FIELD, naturalPerson.givenNames());
                details.put(FAMILY_NAMES_FIELD, naturalPerson.familyNames());
                putNullable(details, PREFERRED_NAME_FIELD, naturalPerson.preferredName());
                putNullable(details, BIRTH_DATE_FIELD, naturalPerson.birthDate());
                putNullable(details, DATE_OF_DEATH_FIELD, naturalPerson.dateOfDeath());
                putNullable(details, BIRTH_COUNTRY_CODE_FIELD, naturalPerson.birthCountryCode());
            }
            case LegalEntityResult legalEntity -> {
                requirePartyType(legalEntity.type(), PartyType.LEGAL_ENTITY);
                details.put("legalName", legalEntity.legalName());
                putNullable(details, "tradeName", legalEntity.tradeName());
                putNullable(details, "legalFormCode", legalEntity.legalFormCode());
                details.put("incorporationCountryCode", legalEntity.incorporationCountryCode());
                putNullable(details, "incorporatedOn", legalEntity.incorporatedOn());
                putNullable(details, "dissolvedOn", legalEntity.dissolvedOn());
            }
        }
        return payload;
    }

    /** Restores the existing safe Party representation without changing registration snapshot semantics. */
    static PartyDetailsResult decodeParty(JsonNode payload) {
        PartyId partyId = new PartyId(requiredUuid(payload, PARTY_ID_FIELD));
        TenantId tenantId = new TenantId(requiredUuid(payload, TENANT_ID_FIELD));
        PartyType type = PartyType.valueOf(requiredText(payload, TYPE_FIELD));
        String displayName = requiredText(payload, DISPLAY_NAME_FIELD);
        PartyRecordStatus recordStatus = PartyRecordStatus.valueOf(requiredText(payload, RECORD_STATUS_FIELD));
        PartyVersion version = new PartyVersion(requiredLong(payload, VERSION_FIELD));
        Instant createdAt = requiredInstant(payload, CREATED_AT_FIELD);
        String createdBy = requiredText(payload, CREATED_BY_FIELD);
        Instant updatedAt = requiredInstant(payload, UPDATED_AT_FIELD);
        String updatedBy = requiredText(payload, UPDATED_BY_FIELD);
        JsonNode details = requiredObject(payload, "details");

        return switch (type) {
            case NATURAL_PERSON -> new NaturalPersonResult(
                    partyId,
                    tenantId,
                    type,
                    displayName,
                    recordStatus,
                    version,
                    requiredText(details, GIVEN_NAMES_FIELD),
                    requiredText(details, FAMILY_NAMES_FIELD),
                    nullableText(details, PREFERRED_NAME_FIELD),
                    nullableDate(details, BIRTH_DATE_FIELD),
                    nullableDate(details, DATE_OF_DEATH_FIELD),
                    nullableText(details, BIRTH_COUNTRY_CODE_FIELD),
                    createdAt,
                    createdBy,
                    updatedAt,
                    updatedBy);
            case LEGAL_ENTITY -> new LegalEntityResult(
                    partyId,
                    tenantId,
                    type,
                    displayName,
                    recordStatus,
                    version,
                    requiredText(details, "legalName"),
                    nullableText(details, "tradeName"),
                    nullableText(details, "legalFormCode"),
                    requiredText(details, "incorporationCountryCode"),
                    nullableDate(details, "incorporatedOn"),
                    nullableDate(details, "dissolvedOn"),
                    createdAt,
                    createdBy,
                    updatedAt,
                    updatedBy);
        };
    }

    private static ObjectNode encodeIdentifier(PartyIdentifierResult identifier) {
        ObjectNode payload = JSON.objectNode();
        payload.put("identifierId", identifier.identifierId().value().toString());
        payload.put(PARTY_ID_FIELD, identifier.partyId().value().toString());
        payload.put("identifierSchemeId", identifier.identifierSchemeId().value().toString());
        payload.put("schemeCode", identifier.schemeCode());
        payload.put("maskedValue", identifier.maskedValue());
        payload.put("status", identifier.status().name());
        payload.put("isPrimary", identifier.isPrimary());
        putNullable(payload, "issuerCode", identifier.issuerCode());
        putNullable(payload, "issuedOn", identifier.issuedOn());
        putNullable(payload, "expiresOn", identifier.expiresOn());
        putNullable(payload, "verifiedAt", identifier.verifiedAt());
        putNullable(payload, "verifiedBy", identifier.verifiedBy());
        payload.put(VERSION_FIELD, identifier.version().value());
        payload.put(CREATED_AT_FIELD, identifier.createdAt().toString());
        payload.put(UPDATED_AT_FIELD, identifier.updatedAt().toString());
        return payload;
    }

    private static PartyIdentifierResult decodeIdentifier(JsonNode payload) {
        return new PartyIdentifierResult(
                new PartyIdentifierId(requiredUuid(payload, "identifierId")),
                new PartyId(requiredUuid(payload, PARTY_ID_FIELD)),
                new IdentifierSchemeId(requiredUuid(payload, "identifierSchemeId")),
                requiredText(payload, "schemeCode"),
                requiredText(payload, "maskedValue"),
                PartyIdentifierStatus.valueOf(requiredText(payload, "status")),
                requiredBoolean(payload, "isPrimary"),
                nullableText(payload, "issuerCode"),
                nullableDate(payload, "issuedOn"),
                nullableDate(payload, "expiresOn"),
                nullableInstant(payload, "verifiedAt"),
                nullableText(payload, "verifiedBy"),
                new PartyIdentifierVersion(requiredLong(payload, VERSION_FIELD)),
                requiredInstant(payload, CREATED_AT_FIELD),
                requiredInstant(payload, UPDATED_AT_FIELD));
    }

    private static void requireSchemaVersion(
            IdempotencyResultSnapshot snapshot,
            short expectedVersion) {
        int payloadVersion = requiredInt(snapshot.payload(), SCHEMA_VERSION_FIELD);
        if (snapshot.schemaVersion() != payloadVersion) {
            throw new IllegalStateException("Idempotency snapshot schema versions do not match");
        }
        if (snapshot.schemaVersion() != expectedVersion) {
            throw new IllegalStateException("Unsupported idempotency snapshot schema version");
        }
    }

    private static void requirePartyType(PartyType actual, PartyType expected) {
        if (actual != expected) {
            throw new IllegalStateException("Idempotency snapshot Party type does not match its details");
        }
    }

    private static JsonNode requiredObject(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        if (!value.isObject()) {
            throw invalidField(fieldName);
        }
        return value;
    }

    private static String requiredText(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        if (!value.isTextual()) {
            throw invalidField(fieldName);
        }
        return value.textValue();
    }

    private static @Nullable String nullableText(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidField(fieldName);
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode object, String fieldName) {
        long value = requiredLong(object, fieldName);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalidField(fieldName);
        }
        return (int) value;
    }

    private static long requiredLong(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidField(fieldName);
        }
        return value.longValue();
    }

    private static boolean requiredBoolean(JsonNode object, String fieldName) {
        JsonNode value = requiredField(object, fieldName);
        if (!value.isBoolean()) {
            throw invalidField(fieldName);
        }
        return value.booleanValue();
    }

    private static UUID requiredUuid(JsonNode object, String fieldName) {
        return UUID.fromString(requiredText(object, fieldName));
    }

    private static Instant requiredInstant(JsonNode object, String fieldName) {
        return Instant.parse(requiredText(object, fieldName));
    }

    private static @Nullable Instant nullableInstant(JsonNode object, String fieldName) {
        String value = nullableText(object, fieldName);
        return value == null ? null : Instant.parse(value);
    }

    private static @Nullable LocalDate nullableDate(JsonNode object, String fieldName) {
        String value = nullableText(object, fieldName);
        return value == null ? null : LocalDate.parse(value);
    }

    private static JsonNode requiredField(JsonNode object, String fieldName) {
        if (!object.isObject()) {
            throw new IllegalStateException("Idempotency snapshot node must be a JSON object");
        }
        JsonNode value = object.get(fieldName);
        if (value == null) {
            throw invalidField(fieldName);
        }
        return value;
    }

    private static IllegalStateException invalidField(String fieldName) {
        return new IllegalStateException("Invalid idempotency snapshot field: " + fieldName);
    }

    private static void putNullable(ObjectNode object, String fieldName, @Nullable Object value) {
        if (value == null) {
            object.putNull(fieldName);
        } else {
            object.put(fieldName, value.toString());
        }
    }
}
