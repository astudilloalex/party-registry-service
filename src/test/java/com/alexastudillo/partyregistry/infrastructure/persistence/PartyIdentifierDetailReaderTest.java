package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe scalar historical reads and nonmutating qualified ownership against reactive PostgreSQL. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierDetailReaderTest {

    private static final LocalDate DATE = LocalDate.of(2025, Month.JANUARY, 12);
    private static final Instant CREATED = DATE.atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000);

    @Inject
    PartyIdentifierDetailReader reader;

    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    @RunOnVertxContext
    void readsEveryHistoricalStateForBothTypesAndAllPartyStates(UniAsserter asserter) {
        List<PartyEntity> parties = new ArrayList<>();
        List<PartyIdentifierEntity> identifiers = new ArrayList<>();
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus partyStatus : PartyRecordStatus.values()) {
                PartyEntity party = party(UUID.randomUUID(), type, partyStatus);
                parties.add(party);
                for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
                    PartyIdentifierEntity identifier = identifier(party, status);
                    identifiers.add(identifier);
                    asserter.assertThat(() -> read(party, identifier.id()), result -> {
                        PartyIdentifierResult safe = result.orElseThrow();
                        assertEquals(status, safe.status());
                        assertEquals(identifier.id(), safe.identifierId().value());
                        assertEquals(identifier.issuerCode(), safe.issuerCode());
                        assertEquals(identifier.expiresOn(), safe.expiresOn());
                        assertEquals(identifier.version(), safe.version().value());
                        assertEquals(CREATED, safe.createdAt());
                    });
                }
            }
        }
        // Persistence must precede every read in the asserter pipeline.
        asserter.surroundWith(work -> persist(parties, identifiers).flatMap(ignored -> work));
    }

    @Test
    @RunOnVertxContext
    void concealsMissingWrongPartyAndCrossTenantIdentifiers(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        PartyEntity owned = party(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        PartyEntity other = party(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
        PartyEntity foreign = party(UUID.randomUUID(), PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        PartyIdentifierEntity otherIdentifier = identifier(other, PartyIdentifierStatus.REVOKED);
        PartyIdentifierEntity foreignIdentifier = identifier(foreign, PartyIdentifierStatus.VERIFIED);
        asserter.execute(() -> persist(List.of(owned, other, foreign), List.of(otherIdentifier, foreignIdentifier)));
        for (UUID id : List.of(otherIdentifier.id(), foreignIdentifier.id(), UUID.randomUUID())) {
            asserter.assertThat(() -> read(owned, id), result -> assertTrue(result.isEmpty()));
        }
        asserter.assertThat(() -> read(foreign, otherIdentifier.id()), result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> read(other, otherIdentifier.id()), result -> assertEquals(otherIdentifier.id(), result.orElseThrow().identifierId().value()));
    }

    @Test
    @RunOnVertxContext
    void leavesProtectedColumnsFlagsAuditAndCurrentProjectionUnchanged(UniAsserter asserter) {
        PartyEntity party = party(UUID.randomUUID(), PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        PartyIdentifierEntity identifier = identifier(party, PartyIdentifierStatus.VERIFIED);
        asserter.execute(() -> persist(List.of(party), List.of(identifier)));
        asserter.execute(() -> snapshot(identifier.id()).invoke(snapshot -> asserter.putData("before", snapshot)));
        asserter.assertThat(() -> read(party, identifier.id()), result -> {
            PartyIdentifierResult safe = result.orElseThrow();
            assertEquals(identifier.maskedValue(), safe.maskedValue());
            assertEquals(identifier.verifiedAt(), safe.verifiedAt());
            assertEquals(identifier.verifiedBy(), safe.verifiedBy());
            assertEquals(identifier.isPrimary(), safe.isPrimary());
            assertFalse(safe.toString().contains("synthetic-private-ciphertext"));
        });
        asserter.assertThat(() -> snapshot(identifier.id()), snapshot -> assertEquals(asserter.getData("before"), snapshot));
        var current = new HibernateReactivePartyIdentifierReadAdapter(sessions);
        asserter.assertThat(() -> timed(current.findCurrentByParty(new TenantId(party.tenantId()), new PartyId(party.id()), DATE.plusDays(2))),
                results -> assertTrue(results.isEmpty()));
        asserter.assertThat(() -> read(party, identifier.id()), result -> assertEquals(PartyIdentifierStatus.VERIFIED, result.orElseThrow().status()));
    }

    @Test
    void projectionExcludesEveryProtectedColumnAndRequiresTheFullOwnerTuple() {
        String query = PartyIdentifierDetailReader.FIND_BY_ID;
        for (String forbidden : List.of("encryptedValue", "normalizedValueHash", "encryptionKeyVersion", "normalizationVersion")) {
            assertFalse(query.contains(forbidden));
        }
        assertTrue(query.contains("identifier.tenantId = :tenantId"));
        assertTrue(query.contains("identifier.partyId = :partyId"));
        assertTrue(query.contains("identifier.id = :identifierId"));
        assertFalse(query.contains("expiresOn >="));
        assertFalse(query.contains("identifier.status in"));
    }

    private Uni<Optional<PartyIdentifierResult>> read(PartyEntity party, UUID identifier) {
        RequestMetadata metadata = new RequestMetadata(new TenantId(party.tenantId()), "reader", UUID.randomUUID());
        return timed(reader.findById(metadata, new PartyId(party.id()), new PartyIdentifierId(identifier)));
    }

    private Uni<String> snapshot(UUID identifier) {
        return timed(sessions.withSession(session -> session.createNativeQuery("""
                SELECT jsonb_build_object('identifier', to_jsonb(i), 'party', to_jsonb(p))::text
                FROM party_identifiers i JOIN parties p ON p.id = i.party_id
                WHERE i.id = :id
                """, String.class).setParameter("id", identifier).getSingleResult()));
    }

    private Uni<Void> persist(List<PartyEntity> parties, List<PartyIdentifierEntity> identifiers) {
        return timed(sessions.withTransaction((session, transaction) -> {
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

    private static PartyEntity party(UUID tenant, PartyType type, PartyRecordStatus status) {
        AuditInfo audit = AuditInfo.initial(CREATED, "creator");
        PartyEntity party = new PartyEntity(UUID.randomUUID(), tenant, type, "Historical read owner", status, audit, 12);
        if (type == PartyType.NATURAL_PERSON) {
            party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(),
                    new NaturalPersonDetails("Historical", "Owner", null, null, null, null), audit));
        } else {
            party.attachLegalEntityDetails(new LegalEntityDetailsEntity(party.id(),
                    new LegalEntityDetails("Historical company", null, null, "EC", null, null), audit));
        }
        return party;
    }

    private static PartyIdentifierEntity identifier(PartyEntity party, PartyIdentifierStatus status) {
        boolean verified = status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.REVOKED;
        return PartyIdentifierEntity.builder().id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID).issuerCode("legacy-authority")
                .encryptedValue("synthetic-private-ciphertext").encryptionKeyVersion((short) 7)
                .normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .maskedValue("***1234").normalizationVersion((short) 3)
                .primary(status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.PENDING_VERIFICATION)
                .status(status).issuedOn(DATE.minusYears(1)).expiresOn(DATE.plusDays(1))
                .verifiedAt(verified ? CREATED.plusSeconds(30) : null).verifiedBy(verified ? "verifier" : null)
                .createdAt(CREATED).createdBy("creator").updatedAt(CREATED.plusSeconds(60)).updatedBy("maintainer")
                .version(9).build();
    }

    private static <T> Uni<T> timed(Uni<T> work) {
        return work.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
