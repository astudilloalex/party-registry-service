package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies that shared assertions reject incomplete pages, status drift, and unsafe error bodies. */
class PartyIdentifierContractAssertionsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void completeEmptyPageRequiresBothExplicitNullDirectionsAndExactCounts() throws Exception {
        ObjectNode page = (ObjectNode) json.readTree("""
                {"status":200,"code":"successful","data":[],"nextCursor":null,"prevCursor":null,
                 "totalElements":0,"totalPages":0,"numberOfElements":0}
                """);
        PartyIdentifierContractAssertions.assertPageResponse(200, page, 50);
        ObjectNode missingCursor = page.deepCopy();
        missingCursor.remove("prevCursor");
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertPageResponse(200, missingCursor, 50));
        ObjectNode wrongCount = page.deepCopy();
        wrongCount.put("numberOfElements", 1);
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertPageResponse(200, wrongCount, 50));
        ObjectNode overflow = page.deepCopy();
        overflow.put("totalElements", (long) Integer.MAX_VALUE + 1);
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertPageResponse(200, overflow, 50));
    }

    @Test
    void failuresRequireOnlyStatusAndCodeWithNoInternalOrBusinessPayload() throws Exception {
        ObjectNode error = (ObjectNode) json.readTree("{\"status\":409,\"code\":\"primary-identifier-conflict\"}");
        PartyIdentifierContractAssertions.assertErrorResponse(409, "primary-identifier-conflict", error);
        ObjectNode leaked = error.deepCopy();
        leaked.put("message", "must-not-leak");
        assertThrows(AssertionError.class,
                () -> PartyIdentifierContractAssertions.assertErrorResponse(409, "primary-identifier-conflict", leaked));
        assertThrows(AssertionError.class,
                () -> PartyIdentifierContractAssertions.assertErrorResponse(412, "primary-identifier-conflict", error));
    }

    @Test
    void safeItemsRejectWrongScalarTypesAndOutOfRangeIndependentVersions() {
        var valid = item(); PartyIdentifierContractAssertions.assertItem(valid);
        for (String field : List.of("identifierId", "partyId", "identifierSchemeId", "maskedValue", "schemeCode", "status", "createdAt", "updatedAt")) {
            var wrong = valid.deepCopy(); wrong.put(field, 42);
            assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(wrong));
        }
        var booleanText = valid.deepCopy(); booleanText.put("isPrimary", "false");
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(booleanText));
        var overflow = valid.deepCopy(); overflow.put("version", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(overflow));
        var negative = valid.deepCopy(); negative.put("version", -1);
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(negative));
    }

    @Test
    void safeItemsRequireCanonicalIdsAndDeclaredStatusWithoutRevalidatingHistoricalIssuerFormat() {
        var valid = item(); valid.put("issuerCode", "historical issuer text"); PartyIdentifierContractAssertions.assertItem(valid);
        var wrongId = valid.deepCopy(); wrongId.put("identifierId", "NOT-A-UUID");
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(wrongId));
        var wrongStatus = valid.deepCopy(); wrongStatus.put("status", "verified");
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(wrongStatus));
        var optionalType = valid.deepCopy(); optionalType.put("verifiedBy", 7);
        assertThrows(AssertionError.class, () -> PartyIdentifierContractAssertions.assertItem(optionalType));
    }

    private ObjectNode item() {
        var item = json.createObjectNode();
        for (String field : List.of("identifierId", "partyId", "identifierSchemeId")) { item.put(field, UUID.randomUUID().toString()); }
        String instant = LocalDate.of(2026, Month.OCTOBER, 9).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        item.put("schemeCode", "FIXTURE_SCHEME"); item.put("maskedValue", "***1234"); item.put("status", "VERIFIED");
        item.put("isPrimary", false); item.put("version", 0); item.put("createdAt", instant); item.put("updatedAt", instant);
        return item;
    }
}
