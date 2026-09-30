package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies all-state root label corrections and the separation of new-write and restoration limits.
 */
class PartyDisplayNameCorrectionTest {

    private static final PartyId PARTY_ID = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT_ID = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 1)
            .atStartOfDay().toInstant(ZoneOffset.UTC);
    private static final Instant UPDATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC);

    @ParameterizedTest
    @MethodSource("parties")
    void canonicalizesTheNewLabelAndPreservesAllOtherIdentityState(Party original) {
        Party corrected = original.correctDisplayName("\u2003Compañía  Straße, S.A._%\u3000", UPDATED, "editor");

        assertEquals("COMPAÑÍA  STRASSE, S.A._%", corrected.displayName());
        assertEquals(original.partyId(), corrected.partyId());
        assertEquals(original.tenantId(), corrected.tenantId());
        assertEquals(original.type(), corrected.type());
        assertEquals(original.recordStatus(), corrected.recordStatus());
        assertEquals(original.version().value() + 1, corrected.version().value());
        assertEquals(original.auditInfo().createdAt(), corrected.auditInfo().createdAt());
        assertEquals(original.auditInfo().createdBy(), corrected.auditInfo().createdBy());
        assertEquals(UPDATED, corrected.auditInfo().updatedAt());
        assertEquals("editor", corrected.auditInfo().updatedBy());
        assertSame(details(original), details(corrected));
        assertEquals("Historical Label", original.displayName());
        assertEquals(7, original.version().value());
        assertEquals(AuditInfo.initial(CREATED, "creator"), original.auditInfo());
    }

    @ParameterizedTest
    @MethodSource("parties")
    void rejectsNullEmptyAndUnicodeBlankLabelsWithoutChangingTheAggregate(Party original) {
        var missing = assertThrows(DomainValidationException.class,
                () -> original.correctDisplayName(null, UPDATED, "editor"));
        assertEquals(DomainViolation.DISPLAY_NAME_REQUIRED, missing.violation());
        for (String blank : List.of("", " \t\r\n", "\u2003\u3000")) {
            var failure = assertThrows(DomainValidationException.class,
                    () -> original.correctDisplayName(blank, UPDATED, "editor"));
            assertEquals(DomainViolation.DISPLAY_NAME_REQUIRED, failure.violation());
        }
        assertEquals("Historical Label", original.displayName());
        assertEquals(7, original.version().value());
        assertEquals(CREATED, original.auditInfo().updatedAt());
    }

    @ParameterizedTest
    @MethodSource("parties")
    void enforcesNormalizedUtf16BoundariesIncludingCaseExpansionAndSupplementaryText(Party original) {
        for (String accepted : List.of("a".repeat(300), "ß".repeat(150), "😀".repeat(150))) {
            Party corrected = original.correctDisplayName("  " + accepted + "  ", UPDATED, "editor");
            assertEquals(300, corrected.displayName().length());
            assertEquals(8, corrected.version().value());
        }
        for (String rejected : List.of("a".repeat(301), "ß".repeat(150) + "a", "😀".repeat(150) + "a")) {
            var failure = assertThrows(DomainValidationException.class,
                    () -> original.correctDisplayName(rejected, UPDATED, "editor"));
            assertEquals(DomainViolation.DISPLAY_NAME_TOO_LONG, failure.violation());
        }
    }

    @ParameterizedTest
    @MethodSource("parties")
    void identicalCanonicalWritesStillAdvanceExactlyOnceAtTheSameAuditInstant(Party original) {
        Party first = original.correctDisplayName(" Label ", UPDATED, "editor");
        Party second = first.correctDisplayName("label", UPDATED, "editor");

        assertEquals(first.displayName(), second.displayName());
        assertEquals(first.auditInfo(), second.auditInfo());
        assertEquals(first.version().value() + 1, second.version().value());
        assertSame(details(original), details(second));
    }

    @Test
    void preservesCodePointBasedRestorationAndHistoricalSubtypeText() {
        String historicalLabel = "😀".repeat(300);
        for (PartyType type : PartyType.values()) {
            Party historical = restored(type, PartyRecordStatus.ARCHIVED, historicalLabel);
            assertEquals(600, historical.displayName().length());
            Party corrected = historical.correctDisplayName("Replacement", UPDATED, "editor");
            assertEquals("REPLACEMENT", corrected.displayName());
            assertEquals(PartyRecordStatus.ARCHIVED, corrected.recordStatus());
            assertSame(details(historical), details(corrected));
            assertEquals(historicalLabel, historical.displayName());
        }
    }

    private static Stream<Party> parties() {
        return Arrays.stream(PartyType.values()).flatMap(type -> Arrays.stream(PartyRecordStatus.values())
                .map(status -> restored(type, status, "Historical Label")));
    }

    private static Party restored(PartyType type, PartyRecordStatus status, String label) {
        var audit = AuditInfo.initial(CREATED, "creator");
        var version = new PartyVersion(7);
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(PARTY_ID, TENANT_ID, label, status, version, audit,
                    new NaturalPersonDetails("😀".repeat(200), "Historical Family", " Mixed Case ",
                            LocalDate.of(2000, Month.JANUARY, 1), null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(PARTY_ID, TENANT_ID, label, status, version, audit,
                    new LegalEntityDetails("😀".repeat(300), " Historical Trade ", "Ltd", "GB",
                            LocalDate.of(2020, Month.JANUARY, 1), null));
        };
    }

    private static Object details(Party party) {
        return switch (party) {
            case NaturalPerson natural -> natural.details();
            case LegalEntity legal -> legal.details();
        };
    }
}
