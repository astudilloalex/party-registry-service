package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Encodes and strictly restores detached nationality result fields for independently versioned snapshots. */
final class NationalitySnapshotFields {

    private static final String NATIONALITY_ID_FIELD = "nationalityId";
    private static final String PARTY_ID_FIELD = "partyId";
    private static final String COUNTRY_CODE_FIELD = "countryCode";
    private static final String IS_PRIMARY_FIELD = "isPrimary";
    private static final String VALID_FROM_FIELD = "validFrom";
    private static final String VALID_UNTIL_FIELD = "validUntil";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String UPDATED_AT_FIELD = "updatedAt";

    private static final Set<String> RESULT_FIELDS = Set.of(
            NATIONALITY_ID_FIELD,
            PARTY_ID_FIELD,
            COUNTRY_CODE_FIELD,
            IS_PRIMARY_FIELD,
            VALID_FROM_FIELD,
            VALID_UNTIL_FIELD,
            CREATED_AT_FIELD,
            UPDATED_AT_FIELD);

    private NationalitySnapshotFields() {
    }

    static ObjectNode result(NationalityResult value) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(NATIONALITY_ID_FIELD, value.nationalityId().value().toString());
        node.put(PARTY_ID_FIELD, value.partyId().value().toString());
        node.put(COUNTRY_CODE_FIELD, value.countryCode());
        node.put(IS_PRIMARY_FIELD, value.isPrimary());
        date(node, VALID_FROM_FIELD, value.validFrom());
        date(node, VALID_UNTIL_FIELD, value.validUntil());
        node.put(CREATED_AT_FIELD, value.createdAt().toString());
        node.put(UPDATED_AT_FIELD, value.updatedAt().toString());
        return node;
    }

    static NationalityResult readResult(JsonNode node) {
        fields(node, RESULT_FIELDS);
        return new NationalityResult(new NationalityId(uuid(node, NATIONALITY_ID_FIELD)),
                new PartyId(uuid(node, PARTY_ID_FIELD)), text(node, COUNTRY_CODE_FIELD),
                booleanValue(node, IS_PRIMARY_FIELD), date(node, VALID_FROM_FIELD), date(node, VALID_UNTIL_FIELD),
                Instant.parse(text(node, CREATED_AT_FIELD)), Instant.parse(text(node, UPDATED_AT_FIELD)));
    }

    static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()
                || !node.properties().stream().allMatch(entry -> expected.contains(entry.getKey()))) {
            throw invalid();
        }
    }

    static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) {
            throw invalid();
        }
        return value.textValue();
    }

    static boolean booleanValue(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isBoolean()) {
            throw invalid();
        }
        return value.booleanValue();
    }

    static UUID uuid(JsonNode node, String key) {
        String encoded = text(node, key);
        UUID value = UUID.fromString(encoded);
        if (!value.toString().equals(encoded)) {
            throw invalid();
        }
        return value;
    }

    static void date(ObjectNode node, String name, LocalDate value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value.toString());
        }
    }

    static LocalDate date(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null) {
            throw invalid();
        }
        if (value.isNull()) {
            return null;
        }
        String encoded = text(node, name);
        LocalDate date = LocalDate.parse(encoded);
        if (!date.toString().equals(encoded)) {
            throw invalid();
        }
        return date;
    }

    static ApplicationException invalid() {
        return new ApplicationException(new ApplicationFailure.PersistenceFailure(),
                new IllegalStateException("Stored nationality snapshot is invalid"));
    }
}
