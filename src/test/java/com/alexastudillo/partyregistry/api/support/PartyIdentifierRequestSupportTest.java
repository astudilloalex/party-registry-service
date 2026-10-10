package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies canonical nested identifier selectors and parent-before-child-before-query syntax precedence. */
class PartyIdentifierRequestSupportTest {

    private static final String PARTY = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String IDENTIFIER = "0198d15c-0557-7a16-a6a8-e3f0ddb5b744";
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());

    @Test
    void acceptsOnlyCanonicalLowercaseIdentifierSelectors() {
        withSupport(support -> {
            assertEquals(UUID.fromString(IDENTIFIER), support.identifierId(IDENTIFIER).value());
            for (String invalid : new String[]{null, "", "1-1-1-1-1", IDENTIFIER.toUpperCase(java.util.Locale.ROOT),
                    " " + IDENTIFIER, IDENTIFIER + " ", "sensitive-selector"}) {
                code("bad-request", () -> support.identifierId(invalid));
            }
        });
    }

    @Test
    void detailChecksPartyBeforeIdentifierAndRetainsAcceptedMetadata() {
        withSupport(support -> {
            code("party-id-invalid", () -> support.detailQuery(METADATA, "bad-party", "bad-identifier"));
            code("bad-request", () -> support.detailQuery(METADATA, PARTY, "bad-identifier"));
            var query = support.detailQuery(METADATA, PARTY, IDENTIFIER);
            assertSame(METADATA, query.requestMetadata());
            assertEquals(UUID.fromString(PARTY), query.partyId().value());
            assertEquals(UUID.fromString(IDENTIFIER), query.identifierId().value());
        });
    }

    @Test
    void listingChecksParentSyntaxBeforeQueryValidation() {
        Map<String, List<String>> invalid = Map.of("unknown", List.of("value"));
        withSupport(support -> {
            code("party-id-invalid", () -> support.listQuery(METADATA, "bad-party", invalid));
            code("bad-request", () -> support.listQuery(METADATA, PARTY, invalid));
            var query = support.listQuery(METADATA, PARTY, Map.of("limit", List.of("1")));
            assertSame(METADATA, query.requestMetadata());
            assertEquals(UUID.fromString(PARTY), query.partyId().value());
            assertEquals(1, query.criteria().limit());
        });
    }

    private static void withSupport(Consumer<PartyIdentifierRequestSupport> check) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            check.accept(new PartyIdentifierRequestSupport(new ApiRequestSupport(factory.getValidator())));
        }
    }

    private static void code(String expected, Runnable action) {
        ApiResponseException failure = assertThrows(ApiResponseException.class, action::run);
        assertEquals(expected, failure.getResponseCode().getCode());
        assertEquals(400, failure.getResponseCode().getStatus());
        assertFalse(failure.toString().contains("sensitive-selector"));
    }
}
