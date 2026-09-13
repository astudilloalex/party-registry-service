package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierReadPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies current masked identifier reads against PostgreSQL and immutable
 * scheme fixtures.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class ReactivePartyIdentifierReadAdapterTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, Month.SEPTEMBER, 12);
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00.123456Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    @Inject
    PartyIdentifierReadPort readPort;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void filtersAllFiveStatusesAndPastTodayFutureOrAbsentExpiration(UniAsserter asserter) {
        PartyEntity party = party(UUID.randomUUID());
        List<PartyIdentifierEntity> identifiers = new ArrayList<>();
        List<UUID> expectedIds = new ArrayList<>();
        for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
            for (LocalDate expiresOn : Arrays.asList(
                    EVALUATED_ON.minusDays(1), EVALUATED_ON, EVALUATED_ON.plusDays(1), null)) {
                // The schema requires an expiration date for the EXPIRED lifecycle.
                if (status == PartyIdentifierStatus.EXPIRED && expiresOn == null) {
                    continue;
                }
                PartyIdentifierEntity identifier = identifier(
                        party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID, status, expiresOn)
                        .build();
                identifiers.add(identifier);
                if ((status == PartyIdentifierStatus.PENDING_VERIFICATION
                        || status == PartyIdentifierStatus.VERIFIED)
                        && (expiresOn == null || !expiresOn.isBefore(EVALUATED_ON))) {
                    expectedIds.add(identifier.id());
                }
            }
        }
        expectedIds.sort(Comparator.comparing(UUID::toString));

        asserter.execute(() -> persist(List.of(party), identifiers));
        asserter.assertThat(() -> read(party), results -> {
            assertEquals(6, results.size());
            assertEquals(expectedIds, ids(results));
            assertEquals(3L, results.stream()
                    .filter(result -> result.status() == PartyIdentifierStatus.PENDING_VERIFICATION)
                    .count());
            assertEquals(3L, results.stream()
                    .filter(result -> result.status() == PartyIdentifierStatus.VERIFIED).count());
            assertThrows(UnsupportedOperationException.class, results::removeFirst);
        });
    }

    @Test
    @RunOnVertxContext
    void isolatesTenantAndPartyAndReturnsImmutableEmptyLists(UniAsserter asserter) {
        PartyEntity party = party(UUID.randomUUID());
        PartyEntity otherParty = party(party.tenantId());
        PartyEntity foreignParty = party(UUID.randomUUID());
        PartyEntity emptyParty = party(party.tenantId());
        PartyIdentifierEntity own = identifier(party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED, null).build();
        PartyIdentifierEntity other = identifier(otherParty, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED, null).build();
        PartyIdentifierEntity foreign = identifier(foreignParty, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.PENDING_VERIFICATION, null).build();

        asserter.execute(() -> persist(List.of(party, otherParty, foreignParty, emptyParty),
                List.of(own, other, foreign)));
        asserter.assertThat(() -> read(party), results -> assertEquals(List.of(own.id()), ids(results)));
        asserter.assertThat(() -> read(otherParty), results -> assertEquals(List.of(other.id()), ids(results)));
        asserter.assertThat(() -> read(foreignParty), results -> assertEquals(List.of(foreign.id()), ids(results)));
        asserter.assertThat(() -> timed(readPort.findCurrentByParty(
                new TenantId(foreignParty.tenantId()), new PartyId(party.id()), EVALUATED_ON)),
                results -> assertTrue(results.isEmpty()));
        asserter.assertThat(() -> timed(readPort.findCurrentByParty(
                new TenantId(party.tenantId()), new PartyId(UUID.randomUUID()), EVALUATED_ON)),
                results -> assertTrue(results.isEmpty()));
        asserter.assertThat(() -> read(emptyParty), results -> {
            assertTrue(results.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> results.add(null));
        });
    }

    @Test
    @RunOnVertxContext
    void ordersByCreationThenUnsignedUuidRegardlessOfInsertionOrder(UniAsserter asserter) {
        PartyEntity party = party(UUID.randomUUID());
        PartyIdentifierEntity oldest = identifier(party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED, null)
                .id(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"))
                .createdAt(CREATED_AT.minusSeconds(1)).build();
        PartyIdentifierEntity lowTie = identifier(party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED, null)
                .id(UUID.fromString("00000000-0000-4000-8000-000000000001")).build();
        PartyIdentifierEntity highTie = identifier(party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.VERIFIED, null)
                .id(UUID.fromString("80000000-0000-4000-8000-000000000001")).build();
        List<UUID> expected = List.of(oldest.id(), lowTie.id(), highTie.id());

        asserter.execute(() -> persist(List.of(party), List.of(highTie, lowTie, oldest)));
        asserter.assertThat(() -> read(party), results -> assertEquals(expected, ids(results)));
        asserter.assertThat(() -> read(party), results -> assertEquals(expected, ids(results)));
    }

    @Test
    @RunOnVertxContext
    void includesAllSchemeLifecyclesAndSubjectsWithoutAPrimaryOnlyFilter(UniAsserter asserter) {
        PartyEntity party = party(UUID.randomUUID());
        List<UUID> schemeIds = List.of(
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID,
                IdentifierSchemeTestFixtures.BOTH_DRAFT_ID,
                IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID,
                IdentifierSchemeTestFixtures.BOTH_RETIRED_ID);
        List<PartyIdentifierEntity> identifiers = new ArrayList<>();
        for (int index = 0; index < schemeIds.size(); index++) {
            identifiers.add(identifier(party, schemeIds.get(index), PartyIdentifierStatus.VERIFIED,
                    EVALUATED_ON.plusDays(1))
                    .primary(index == 0).createdAt(CREATED_AT.plusSeconds(index)).build());
        }

        asserter.execute(() -> persist(List.of(party), identifiers));
        asserter.assertThat(() -> read(party), results -> {
            assertEquals(IdentifierSchemeTestFixtures.ALL_CODES,
                    results.stream().map(PartyIdentifierResult::schemeCode).toList());
            assertEquals(schemeIds, results.stream()
                    .map(result -> result.identifierSchemeId().value()).toList());
            assertTrue(results.getFirst().isPrimary());
            assertTrue(results.subList(1, results.size()).stream().noneMatch(PartyIdentifierResult::isPrimary));
        });
    }

    @Test
    @RunOnVertxContext
    void returnsMoreThanFiftyCompleteMaskedProjectionsWithNullableMetadata(UniAsserter asserter) {
        PartyEntity party = party(UUID.randomUUID());
        List<PartyIdentifierEntity> identifiers = new ArrayList<>();
        for (int index = 0; index < 75; index++) {
            identifiers.add(identifier(party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                    PartyIdentifierStatus.VERIFIED, EVALUATED_ON)
                    .createdAt(CREATED_AT.plusMillis(index)).build());
        }
        PartyIdentifierEntity nullable = identifier(party, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                PartyIdentifierStatus.PENDING_VERIFICATION, null)
                .issuerCode(null).issuedOn(null).createdAt(CREATED_AT.plusSeconds(1)).build();
        identifiers.add(nullable);

        asserter.execute(() -> persist(List.of(party), identifiers));
        asserter.assertThat(() -> read(party), results -> {
            assertEquals(76, results.size());
            for (int index = 0; index < identifiers.size(); index++) {
                PartyIdentifierEntity expected = identifiers.get(index);
                assertEquals(new PartyIdentifierResult(
                        new PartyIdentifierId(expected.id()), new PartyId(party.id()),
                        new IdentifierSchemeId(expected.identifierSchemeId()),
                        IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE, expected.maskedValue(),
                        expected.status(), expected.isPrimary(), expected.issuerCode(), expected.issuedOn(),
                        expected.expiresOn(), expected.verifiedAt(), expected.verifiedBy(),
                        new PartyIdentifierVersion(expected.version()), expected.createdAt(), expected.updatedAt()),
                        results.get(index));
            }
            assertFalse(results.toString().contains("unreadable-test-ciphertext"));
            PartyIdentifierResult replacement = results.getLast();
            assertThrows(UnsupportedOperationException.class, () -> results.set(0, replacement));
        });
    }

    private Uni<List<PartyIdentifierResult>> read(PartyEntity party) {
        return timed(readPort.findCurrentByParty(
                new TenantId(party.tenantId()), new PartyId(party.id()), EVALUATED_ON));
    }

    private Uni<Void> persist(List<PartyEntity> parties, List<PartyIdentifierEntity> identifiers) {
        return timed(sessionFactory.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (PartyEntity party : parties) {
                sequence = sequence.call(() -> session.persist(party));
            }
            for (PartyIdentifierEntity identifier : identifiers) {
                sequence = sequence.call(() -> session.persist(identifier));
            }
            return sequence.call(session::flush);
        }));
    }

    private static PartyEntity party(UUID tenantId) {
        AuditInfo audit = AuditInfo.initial(CREATED_AT, "identifier-read-fixture");
        PartyEntity party = new PartyEntity(UUID.randomUUID(), tenantId, PartyType.NATURAL_PERSON,
                "Identifier Read Fixture", PartyRecordStatus.DRAFT,
                audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(),
                new NaturalPersonDetails("Identifier", "Read Fixture", null, null, null, null), audit));
        return party;
    }

    private static PartyIdentifierEntity.Builder identifier(
            PartyEntity party,
            UUID schemeId,
            PartyIdentifierStatus status,
            LocalDate expiresOn) {
        boolean verified = status == PartyIdentifierStatus.VERIFIED;
        return PartyIdentifierEntity.builder()
                .id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(schemeId).issuerCode("TEST-ISSUER")
                .encryptedValue("unreadable-test-ciphertext").encryptionKeyVersion((short) 1)
                .normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .maskedValue("********1234").normalizationVersion((short) 1).primary(false)
                .status(status).issuedOn(EVALUATED_ON.minusYears(1)).expiresOn(expiresOn)
                .verifiedAt(verified ? CREATED_AT.plusSeconds(30) : null)
                .verifiedBy(verified ? "test-verifier" : null)
                .createdAt(CREATED_AT).createdBy("identifier-read-fixture")
                .updatedAt(UPDATED_AT).updatedBy("identifier-read-fixture").version(0);
    }

    private static List<UUID> ids(List<PartyIdentifierResult> results) {
        return results.stream().map(result -> result.identifierId().value()).toList();
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(TIMEOUT).fail();
    }
}
