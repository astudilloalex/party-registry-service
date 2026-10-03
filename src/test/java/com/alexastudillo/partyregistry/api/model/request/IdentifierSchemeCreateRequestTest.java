package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeRequestParameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies exact creation input and strict transport validation before business evaluation. */
class IdentifierSchemeCreateRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IdentifierSchemeRequestParameters parameters = new IdentifierSchemeRequestParameters();
    private static final String VALID = """
            {"code":" Mixed-Code ","issuingCountryCode":"EC","category":"OTHER",
             "applicableSubjectType":"BOTH","name":" Name ",
             "normalizerKey":" historical ","validatorKey":"unknown"}
            """;

    @Test
    void preservesExactTextAndMaterializesOnlyApprovedDefaults() throws Exception {
        var input = parameters.create(read(VALID));
        assertEquals(" Mixed-Code ", input.code());
        assertEquals(" Name ", input.name());
        assertEquals(" historical ", input.normalizerKey());
        assertFalse(input.requiresExpiration());
        assertNull(input.description());
        assertNull(input.minimumLength());
        assertNull(input.maximumLength());
        assertEquals(input, parameters.create(read(add("\"description\":null,\"minimumLength\":null,"
                + "\"maximumLength\":null,\"requiresExpiration\":false"))));
        assertTrue(parameters.create(read(add("\"requiresExpiration\":true"))).requiresExpiration());
    }

    @Test
    void retainsArbitraryIntegerBoundsWithoutSemanticRangeOrCoherenceChecks() throws Exception {
        String huge = "9".repeat(300);
        var input = parameters.create(read(add("\"minimumLength\":" + huge + ",\"maximumLength\":-7")));
        assertEquals(new BigInteger(huge), input.minimumLength());
        assertEquals(BigInteger.valueOf(-7), input.maximumLength());
        assertEquals(BigInteger.ZERO, parameters.create(read(add("\"minimumLength\":0"))).minimumLength());
    }

    @Test
    void distinguishesBodyAndCodeFailuresAndCountsUnicodeCodePoints() throws Exception {
        assertCode(PartyResponseCode.REQUEST_BODY_REQUIRED, null);
        assertCode(PartyResponseCode.REQUEST_BODY_REQUIRED, read("null"));
        assertCode(PartyResponseCode.IDENTIFIER_SCHEME_CODE_REQUIRED, read("{}"));
        for (String value : new String[]{"null", "\"\"", "\"  \""}) {
            assertCode(PartyResponseCode.IDENTIFIER_SCHEME_CODE_REQUIRED,
                    read(VALID.replace("\" Mixed-Code \"", value)));
        }
        String supplementary = "😀";
        assertEquals(supplementary.repeat(64), parameters.create(read(
                VALID.replace(" Mixed-Code ", supplementary.repeat(64)))).code());
        assertCode(PartyResponseCode.IDENTIFIER_SCHEME_CODE_TOO_LONG,
                read(VALID.replace(" Mixed-Code ", supplementary.repeat(65))));
        parameters.create(read(VALID.replace(" Name ", supplementary.repeat(150))
                .replace(" historical ", supplementary.repeat(64)).replace("unknown", supplementary.repeat(64))));
        parameters.create(read(add("\"description\":\"" + supplementary.repeat(500) + "\"")));
        assertCode(PartyResponseCode.BAD_REQUEST, read(add("\"description\":\"" + supplementary.repeat(501) + "\"")));
    }

    @Test
    void rejectsRequiredTextCountryAndEnumFormatsWithoutNormalization() throws Exception {
        for (String original : new String[]{" Name ", " historical ", "unknown"}) {
            assertCode(PartyResponseCode.BAD_REQUEST, read(VALID.replace(original, " ")));
            int limit = original.equals(" Name ") ? 150 : 64;
            assertCode(PartyResponseCode.BAD_REQUEST, read(VALID.replace(original, "x".repeat(limit + 1))));
            assertCode(PartyResponseCode.BAD_REQUEST, read(VALID.replace('"' + original + '"', "null")));
        }
        for (String country : new String[]{"ec", " EC", "E1", "ÉC", "ＥＣ", "E", "ECC"}) {
            assertCode(PartyResponseCode.BAD_REQUEST, read(VALID.replace("EC", country)));
        }
        for (String field : new String[]{"issuingCountryCode", "category", "applicableSubjectType", "name", "normalizerKey", "validatorKey"}) {
            var tree = mapper.readTree(VALID);
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove(field);
            assertCode(PartyResponseCode.BAD_REQUEST, read(tree.toString()));
        }
        for (String category : new String[]{"NATIONAL_ID", "TAX_ID", "PASSPORT", "RESIDENCE_PERMIT", "LEGAL_REGISTRATION_NUMBER", "OTHER"}) {
            assertEquals(category, parameters.create(read(VALID.replace("OTHER", category))).category().name());
        }
        for (String subject : new String[]{"NATURAL_PERSON", "LEGAL_ENTITY", "BOTH"}) {
            assertEquals(subject, parameters.create(read(VALID.replace("BOTH", subject))).applicableSubjectType().name());
        }
    }

    @Test
    void rejectsUnknownDuplicateNonobjectCoercedAndTrailingContent() {
        for (String body : new String[]{"[]", "7", "true", "\"text\"", add("\"id\":null"),
                add("\"code\":\"other\""), add("\"description\":null,\"description\":null"),
                add("\"minimumLength\":\"3\""), add("\"minimumLength\":3.0"), add("\"maximumLength\":3e2"),
                add("\"requiresExpiration\":null"), add("\"requiresExpiration\":\"false\""),
                add("\"requiresExpiration\":0"), add("\"description\":false"), add("\"maximumLength\":[]"),
                VALID.replace("\"OTHER\"", "\"other\""), VALID.replace("\"BOTH\"", "1"),
                VALID.replace("\" Name \"", "42"), VALID + " {}", "{", "{\"code\":"}) {
            assertThrows(JsonProcessingException.class, () -> read(body), body);
        }
    }

    private IdentifierSchemeCreateRequest read(String body) throws Exception {
        return mapper.readValue(body, IdentifierSchemeCreateRequest.class);
    }

    private void assertCode(PartyResponseCode code, IdentifierSchemeCreateRequest request) {
        assertEquals(code, assertThrows(ApiResponseException.class, () -> parameters.create(request)).getResponseCode());
    }

    private static String add(String fields) {
        return VALID.stripTrailing().substring(0, VALID.stripTrailing().length() - 1) + ',' + fields + '}';
    }
}
