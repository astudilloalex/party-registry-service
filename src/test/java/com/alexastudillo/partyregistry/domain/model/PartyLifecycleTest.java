package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the complete Party lifecycle matrix, retained history, and exact bounded version advances.
 */
class PartyLifecycleTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 1)
            .atStartOfDay().toInstant(ZoneOffset.UTC);
    private static final Instant UPDATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC);

    @ParameterizedTest
    @MethodSource("acceptedTransitions")
    void changesOnlyLifecycleVersionAndModificationAudit(
            PartyType type, PartyRecordStatus before, String action, PartyRecordStatus after) {
        Party original = restored(type, before, 4);
        Party changed = transition(original, action, UPDATED);

        assertEquals(after, changed.recordStatus());
        assertEquals(5, changed.version().value());
        assertEquals(original.partyId(), changed.partyId());
        assertEquals(original.tenantId(), changed.tenantId());
        assertEquals(original.type(), changed.type());
        assertEquals(original.displayName(), changed.displayName());
        assertSame(details(original), details(changed));
        assertEquals(original.auditInfo().createdAt(), changed.auditInfo().createdAt());
        assertEquals(original.auditInfo().createdBy(), changed.auditInfo().createdBy());
        assertEquals(UPDATED, changed.auditInfo().updatedAt());
        assertEquals("operator", changed.auditInfo().updatedBy());
        assertEquals(before, original.recordStatus());
        assertEquals(4, original.version().value());
        assertEquals(AuditInfo.initial(CREATED, "creator"), original.auditInfo());
    }

    @ParameterizedTest
    @MethodSource("rejectedTransitions")
    void rejectsEveryDisallowedTransitionWithoutChangingTheOriginal(
            PartyType type, PartyRecordStatus before, String action, DomainViolation expected) {
        Party original = restored(type, before, 4);
        var failure = assertThrows(DomainValidationException.class, () -> transition(original, action, UPDATED));

        assertEquals(expected, failure.violation());
        assertEquals(before, original.recordStatus());
        assertEquals(4, original.version().value());
        assertEquals(AuditInfo.initial(CREATED, "creator"), original.auditInfo());
    }

    @ParameterizedTest
    @MethodSource("acceptedTransitions")
    void rejectsOverflowInsteadOfWrappingTheRootVersion(
            PartyType type, PartyRecordStatus before, String action, PartyRecordStatus after) {
        Party original = restored(type, before, Long.MAX_VALUE);
        var failure = assertThrows(DomainValidationException.class, () -> transition(original, action, UPDATED));

        assertEquals(DomainViolation.PARTY_VERSION_OVERFLOW, failure.violation());
        assertEquals(before, original.recordStatus());
        assertEquals(Long.MAX_VALUE, original.version().value());
        assertEquals(after, transition(restored(type, before, Long.MAX_VALUE - 1), action, UPDATED).recordStatus());
        assertEquals(Long.MAX_VALUE,
                transition(restored(type, before, Long.MAX_VALUE - 1), action, UPDATED).version().value());
    }

    @ParameterizedTest
    @MethodSource("acceptedTransitions")
    void refusesAuditTimeRegressionForOtherwisePermittedTransitions(
            PartyType type, PartyRecordStatus before, String action, PartyRecordStatus after) {
        Party original = restored(type, before, 4);
        Instant beforeCreation = CREATED.minusSeconds(1);
        var failure = assertThrows(DomainValidationException.class,
                () -> transition(original, action, beforeCreation));

        assertEquals(DomainViolation.AUDIT_TIMESTAMP_ORDER, failure.violation());
        assertEquals(before, original.recordStatus());
        assertEquals(4, original.version().value());
        assertEquals(after, transition(original, action, CREATED).recordStatus());
    }

    private static Stream<Arguments> acceptedTransitions() {
        return Arrays.stream(PartyType.values()).flatMap(type -> Stream.of(
                Arguments.of(type, PartyRecordStatus.DRAFT, "activate", PartyRecordStatus.ACTIVE),
                Arguments.of(type, PartyRecordStatus.ACTIVE, "deactivate", PartyRecordStatus.INACTIVE),
                Arguments.of(type, PartyRecordStatus.DRAFT, "archive", PartyRecordStatus.ARCHIVED),
                Arguments.of(type, PartyRecordStatus.ACTIVE, "archive", PartyRecordStatus.ARCHIVED),
                Arguments.of(type, PartyRecordStatus.INACTIVE, "archive", PartyRecordStatus.ARCHIVED)));
    }

    private static Stream<Arguments> rejectedTransitions() {
        return Arrays.stream(PartyType.values()).flatMap(type -> Stream.of(
                Arguments.of(type, PartyRecordStatus.ACTIVE, "activate", DomainViolation.PARTY_ACTIVATION_INVALID_STATE),
                Arguments.of(type, PartyRecordStatus.INACTIVE, "activate", DomainViolation.PARTY_ACTIVATION_INVALID_STATE),
                Arguments.of(type, PartyRecordStatus.ARCHIVED, "activate", DomainViolation.PARTY_ACTIVATION_INVALID_STATE),
                Arguments.of(type, PartyRecordStatus.DRAFT, "deactivate", DomainViolation.PARTY_DEACTIVATION_INVALID_STATE),
                Arguments.of(type, PartyRecordStatus.INACTIVE, "deactivate", DomainViolation.PARTY_DEACTIVATION_INVALID_STATE),
                Arguments.of(type, PartyRecordStatus.ARCHIVED, "deactivate", DomainViolation.PARTY_DEACTIVATION_INVALID_STATE),
                Arguments.of(type, PartyRecordStatus.ARCHIVED, "archive", DomainViolation.PARTY_ARCHIVAL_INVALID_STATE)));
    }

    private static Party transition(Party party, String action, Instant instant) {
        return switch (action) {
            case "activate" -> party.activate(instant, "operator");
            case "deactivate" -> party.deactivate(instant, "operator");
            case "archive" -> party.archive(instant, "operator");
            default -> throw new IllegalArgumentException("Unknown test action");
        };
    }

    private static Party restored(PartyType type, PartyRecordStatus status, long version) {
        var partyId = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
        var tenantId = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
        var audit = AuditInfo.initial(CREATED, "creator");
        String historicalName = "😀".repeat(300);
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(partyId, tenantId, historicalName, status,
                    new PartyVersion(version), audit,
                    new NaturalPersonDetails(" Mixed ", "Case", "Label", null, null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(partyId, tenantId, historicalName, status,
                    new PartyVersion(version), audit,
                    new LegalEntityDetails("Mixed Company", " Trade ", "Ltd", "GB", null, null));
        };
    }

    private static Object details(Party party) {
        return switch (party) {
            case NaturalPerson natural -> natural.details();
            case LegalEntity legal -> legal.details();
        };
    }
}
