package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventCatalogContractTest {
    private static final Set<String> EVENT_TYPES = Set.of(
            "party.created.v1", "party.updated.v1", "party.activated.v1", "party.inactivated.v1",
            "party.archived.v1", "party.nationality-added.v1", "party.nationality-updated.v1",
            "party.nationality-removed.v1", "party.identifier-created.v1", "party.identifier-updated.v1",
            "party.identifier-verified.v1", "party.identifier-rejected.v1", "party.identifier-expired.v1",
            "party.identifier-revoked.v1");
    private static final Set<String> PROHIBITED_FIELDS = Set.of(
            "value", "plaintext", "ciphertext", "normalizedValueHash", "fingerprint", "maskedValue",
            "keyMaterial", "givenNames", "familyNames", "preferredName", "birthDate", "legalName",
            "verifiedBy", "issuerCode");

    @Test
    void catalogContainsExactlyApprovedVersionedEventsAndRequiredStableEnvelope() {
        JsonNode schema = ContractSources.json(ContractSources.EVENT_SCHEMA);
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.path("$schema").asText());
        JsonNode definitions = schema.path("$defs");
        Set<String> actualTypes = new HashSet<>();
        definitions.fields().forEachRemaining(entry -> {
            JsonNode eventType = entry.getValue().path("properties").path("eventType").path("const");
            if (eventType.isTextual()) {
                actualTypes.add(eventType.asText());
            }
        });
        assertEquals(EVENT_TYPES, actualTypes);
        assertEquals(Set.of("eventId", "eventType", "schemaVersion", "tenantId", "aggregateType",
                        "aggregateId", "aggregateVersion", "occurredAt", "payload"),
                toSet(definitions.path("base").path("required")));
        assertEquals(1, definitions.path("base").path("properties").path("schemaVersion").path("const").asInt());
        assertEquals(0, definitions.path("base").path("properties").path("aggregateVersion").path("minimum").asInt());
    }

    @Test
    void identifierAndPartyPayloadsExcludePlaintextAndUnnecessaryPersonalData() {
        JsonNode definitions = ContractSources.json(ContractSources.EVENT_SCHEMA).path("$defs");
        Set<String> payloadFields = new HashSet<>();
        for (String name : List.of("partyPayload", "partyStatusPayload", "nationalityPayload",
                "identifierPayload", "identifierStatusPayload")) {
            definitions.path(name).path("properties").fieldNames().forEachRemaining(payloadFields::add);
            assertFalse(definitions.path(name).path("additionalProperties").asBoolean(true), name);
        }
        PROHIBITED_FIELDS.forEach(field -> assertFalse(payloadFields.contains(field), "Event payload leaks " + field));
        assertFalse(EVENT_TYPES.stream().anyMatch(type -> type.contains("scheme") || type.contains("outbox")
                || type.contains("database") || type.contains("audit") || type.contains("decrypt")));
    }

    @Test
    void everySyntheticExampleValidatesAndCarriesNoProhibitedField() throws IOException {
        JsonNode schemaNode = ContractSources.json(ContractSources.EVENT_SCHEMA);
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        try (Stream<Path> paths = Files.list(Path.of("api/events/v1/examples"))) {
            List<Path> examples = paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            assertFalse(examples.isEmpty());
            for (Path example : examples) {
                JsonNode event = ContractSources.json(example);
                assertTrue(schema.validate(event).isEmpty(), example.toString());
                Set<String> fields = new HashSet<>();
                collectFieldNames(event, fields);
                PROHIBITED_FIELDS.forEach(field -> assertFalse(fields.contains(field), example + " leaks " + field));
            }
        }
    }

    private static Set<String> toSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static void collectFieldNames(JsonNode node, Set<String> fields) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                fields.add(entry.getKey());
                collectFieldNames(entry.getValue(), fields);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, fields));
        }
    }
}
