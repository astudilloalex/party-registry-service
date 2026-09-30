package com.alexastudillo.partyregistry.support;

import java.util.List;
import java.util.UUID;

/**
 * Exposes stable identifiers for the test-only identifier-scheme catalog.
 */
public final class IdentifierSchemeTestFixtures {

    public static final UUID NATURAL_ACTIVE_ID = uuid("0198d111-08f1-7e48-b291-399bbb9cd601");
    public static final String NATURAL_ACTIVE_CODE = "TEST_NATURAL_ACTIVE";
    public static final UUID LEGAL_ACTIVE_ID = uuid("0198d111-08f1-7e48-b291-399bbb9cd602");
    public static final String LEGAL_ACTIVE_CODE = "TEST_LEGAL_ACTIVE";
    public static final UUID BOTH_EXPIRING_ID = uuid("0198d111-08f1-7e48-b291-399bbb9cd603");
    public static final String BOTH_EXPIRING_CODE = "TEST_BOTH_EXPIRING";
    public static final UUID BOTH_DRAFT_ID = uuid("0198d111-08f1-7e48-b291-399bbb9cd604");
    public static final String BOTH_DRAFT_CODE = "TEST_BOTH_DRAFT";
    public static final UUID BOTH_DEPRECATED_ID = uuid("0198d111-08f1-7e48-b291-399bbb9cd605");
    public static final String BOTH_DEPRECATED_CODE = "TEST_BOTH_DEPRECATED";
    public static final UUID BOTH_RETIRED_ID = uuid("0198d111-08f1-7e48-b291-399bbb9cd606");
    public static final String BOTH_RETIRED_CODE = "TEST_BOTH_RETIRED";

    public static final List<String> ALL_CODES = List.of(
            NATURAL_ACTIVE_CODE,
            LEGAL_ACTIVE_CODE,
            BOTH_EXPIRING_CODE,
            BOTH_DRAFT_CODE,
            BOTH_DEPRECATED_CODE,
            BOTH_RETIRED_CODE);

    private IdentifierSchemeTestFixtures() {
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
