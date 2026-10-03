package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies both current global selectors, all retained states, exact Unicode codes, and sanitized absence. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSchemeReadContractTest {
    @Test
    void bothSelectorsAgreeAcrossAllStatesAndTenantsWithoutChangingAudit() {
        var first = Context.fresh();
        var other = Context.fresh();
        for (var entry : Map.of(IdentifierSchemeTestFixtures.BOTH_DRAFT_CODE, "DRAFT",
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE, "ACTIVE", IdentifierSchemeTestFixtures.BOTH_DEPRECATED_CODE, "DEPRECATED",
                IdentifierSchemeTestFixtures.BOTH_RETIRED_CODE, "RETIRED").entrySet()) {
            var byCode = success(first.request().get(ROOT + "/by-code/{code}", entry.getKey()), 200, first);
            assertEquals(entry.getValue(), byCode.get("status"));
            assertEquals(byCode, success(other.request().get(ROOT + "/" + byCode.get("id")), 200, other));
            assertEquals(byCode, success(first.request().get(ROOT + "/by-code/{code}", entry.getKey()), 200, first));
        }
    }

    @Test
    void preservesExactCasePaddingAndSupplementaryUnicodeAtTheCodePointBoundary() {
        var context = Context.fresh();
        for (String code : List.of(" Padded-" + UUID.randomUUID() + " ", "😀".repeat(64))) {
            var created = create(context, code);
            assertEquals(created, success(context.request().get(ROOT + "/by-code/{code}", code), 200, context));
            assertEquals(created, success(context.request().get(ROOT + "/" + created.get("id")), 200, context));
            if (!code.equals(code.trim())) {
                error(context.request().get(ROOT + "/by-code/{code}", code.trim()), 404, "identifier-scheme-not-found", context);
                error(context.request().get(ROOT + "/by-code/{code}", code.toUpperCase(java.util.Locale.ROOT)), 404, "identifier-scheme-not-found", context);
            }
        }
        error(context.request().get(ROOT + "/by-code/{code}", "😀".repeat(65)), 400, "identifier-scheme-code-too-long", context);
        error(context.request().get(ROOT + "/by-code/{code}", " "), 400, "identifier-scheme-code-required", context);
    }

    @Test
    void separatesMalformedSelectorsFromValidUnknownTargets() {
        var context = Context.fresh();
        assertAll(
                () -> {
                    for (String id : List.of("invalid", "1-1-1-1-1", "0198D111-08F1-7E48-B291-399BBB9CD604", UUID.randomUUID() + "x")) {
                        error(context.request().get(ROOT + "/" + id), 400, "identifier-scheme-id-invalid", context);
                    }
                },
                () -> error(context.request().get(ROOT + "/" + UUID.randomUUID()), 404, "identifier-scheme-not-found", context),
                () -> error(context.request().get(ROOT + "/by-code/{code}", uniqueCode()), 404, "identifier-scheme-not-found", context)
        );
    }
}
