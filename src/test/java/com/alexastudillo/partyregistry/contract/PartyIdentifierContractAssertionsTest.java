package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
}
