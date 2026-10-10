package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Encodes and validates only the fixed schema-one safe identifier result fields without reflective model binding. */
final class PartyIdentifierSnapshotFields {
    private static final String ID = "identifierId";
    private static final String PARTY = "partyId";
    private static final String SCHEME = "identifierSchemeId";
    private static final String CODE = "schemeCode";
    private static final String MASK = "maskedValue";
    private static final String STATUS = "status";
    private static final String PRIMARY = "isPrimary";
    private static final String ISSUER = "issuerCode";
    private static final String ISSUED = "issuedOn";
    private static final String EXPIRES = "expiresOn";
    private static final String VERIFIED_AT = "verifiedAt";
    private static final String VERIFIED_BY = "verifiedBy";
    private static final String VERSION = "version";
    private static final String CREATED = "createdAt";
    private static final String UPDATED = "updatedAt";
    private static final Set<String> RESULT_FIELDS = Set.of(ID, PARTY, SCHEME, CODE, MASK, STATUS, PRIMARY,
            ISSUER, ISSUED, EXPIRES, VERIFIED_AT, VERIFIED_BY, VERSION, CREATED, UPDATED);

    private PartyIdentifierSnapshotFields() { }

    static ObjectNode encode(PartyIdentifierResult result) {
        validate(result);
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(ID, result.identifierId().value().toString());
        node.put(PARTY, result.partyId().value().toString());
        node.put(SCHEME, result.identifierSchemeId().value().toString());
        node.put(CODE, result.schemeCode());
        node.put(MASK, result.maskedValue());
        node.put(STATUS, result.status().name());
        node.put(PRIMARY, result.isPrimary());
        node.put(ISSUER, result.issuerCode());
        LocalDate issued = result.issuedOn();
        LocalDate expires = result.expiresOn();
        Instant verified = result.verifiedAt();
        node.put(ISSUED, issued == null ? null : issued.toString());
        node.put(EXPIRES, expires == null ? null : expires.toString());
        node.put(VERIFIED_AT, verified == null ? null : verified.toString());
        node.put(VERIFIED_BY, result.verifiedBy());
        node.put(VERSION, result.version().value());
        node.put(CREATED, result.createdAt().toString());
        node.put(UPDATED, result.updatedAt().toString());
        return node;
    }

    static PartyIdentifierResult decode(JsonNode node) {
        fields(node, RESULT_FIELDS);
        String issued = nullableText(node, ISSUED);
        String expires = nullableText(node, EXPIRES);
        String verified = nullableText(node, VERIFIED_AT);
        var result = new PartyIdentifierResult(new PartyIdentifierId(uuid(node, ID)), new PartyId(uuid(node, PARTY)),
                new IdentifierSchemeId(uuid(node, SCHEME)), text(node, CODE), text(node, MASK),
                PartyIdentifierStatus.valueOf(text(node, STATUS)), bool(node, PRIMARY), nullableText(node, ISSUER),
                issued == null ? null : LocalDate.parse(issued), expires == null ? null : LocalDate.parse(expires),
                verified == null ? null : Instant.parse(verified), nullableText(node, VERIFIED_BY),
                new PartyIdentifierVersion(number(node, VERSION)), Instant.parse(text(node, CREATED)), Instant.parse(text(node, UPDATED)));
        validate(result);
        return result;
    }

    private static void validate(PartyIdentifierResult result) {
        bounded(result.schemeCode(), 64);
        bounded(result.maskedValue(), 64);
        bounded(result.issuerCode(), 64);
        bounded(result.verifiedBy(), 128);
        LocalDate issued = result.issuedOn();
        LocalDate expires = result.expiresOn();
        if (result.createdAt().isAfter(result.updatedAt()) || issued != null && expires != null && expires.isBefore(issued)) {
            throw invalid();
        }
        validateVerification(result);
    }

    private static void validateVerification(PartyIdentifierResult result) {
        Instant verified = result.verifiedAt();
        String actor = result.verifiedBy();
        if ((verified == null) != (actor == null) || actor != null && actor.isBlank()) {
            throw invalid();
        }
        if ((result.status() == PartyIdentifierStatus.VERIFIED || result.status() == PartyIdentifierStatus.REVOKED) && verified == null) {
            throw invalid();
        }
    }

    private static void bounded(@Nullable String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) { throw invalid(); }
    }

    static void fields(JsonNode node, Set<String> expected) {
        if (!node.isObject() || node.size() != expected.size() || !node.properties().stream().allMatch(entry -> expected.contains(entry.getKey()))) {
            throw invalid();
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) { throw invalid(); }
        return value.textValue();
    }

    static @Nullable String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() ? null : text(node, field);
    }

    static long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) { throw invalid(); }
        return value.longValue();
    }

    static boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) { throw invalid(); }
        return value.booleanValue();
    }

    static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) { throw invalid(); }
        return parsed;
    }

    static IllegalStateException invalid() { return new IllegalStateException("Stored identifier completion is invalid"); }
}
