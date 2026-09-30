package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies nullable nationality bounds and audit columns against the
 * Flyway-managed PostgreSQL table.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class PartyNationalityEntityPersistenceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-23T10:00:00Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void roundTripsUuidCountryPrimaryNullableDatesAndAudit(UniAsserter asserter) {
        UUID partyId = UUID.randomUUID();
        PartyEntity party = new PartyEntity(partyId, UUID.randomUUID(), PartyType.LEGAL_ENTITY,
                "Nationality fixture", PartyRecordStatus.DRAFT,
                AuditInfo.initial(CREATED_AT, "creator"), 0);
        PartyNationalityEntity open = new PartyNationalityEntity(new PartyNationality(
                new NationalityId(UUID.randomUUID()), new PartyId(partyId), "EC", false,
                new NationalityPeriod(null, null), AuditInfo.initial(CREATED_AT, "creator")));
        PartyNationalityEntity dated = new PartyNationalityEntity(new PartyNationality(
                new NationalityId(UUID.randomUUID()), new PartyId(partyId), "CO", true,
                new NationalityPeriod(LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 30)),
                new AuditInfo(CREATED_AT, "creator", UPDATED_AT, "editor")));

        asserter.execute(() -> sessionFactory.withTransaction((session, transaction) -> session.persist(party)
                .call(() -> session.persist(open))
                .call(() -> session.persist(dated))
                .call(session::flush)).ifNoItem().after(Duration.ofSeconds(15)).fail());
        asserter.assertThat(() -> sessionFactory
                .withSession(session -> session.find(PartyNationalityEntity.class, open.id()))
                .ifNoItem().after(Duration.ofSeconds(15)).fail(), restored -> {
                    NationalityResult result = restored.toResult();
                    assertEquals(open.id(), result.nationalityId().value());
                    assertEquals(partyId, result.partyId().value());
                    assertEquals("EC", result.countryCode());
                    assertFalse(result.isPrimary());
                    assertNull(result.validFrom());
                    assertNull(result.validUntil());
                    assertEquals(CREATED_AT, result.createdAt());
                    assertEquals(CREATED_AT, result.updatedAt());
                });
        asserter.assertThat(() -> sessionFactory
                .withSession(session -> session.find(PartyNationalityEntity.class, dated.id()))
                .ifNoItem().after(Duration.ofSeconds(15)).fail(), restored -> {
                    NationalityResult result = restored.toResult();
                    assertEquals("CO", result.countryCode());
                    assertEquals(true, result.isPrimary());
                    assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 1), result.validFrom());
                    assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 30), result.validUntil());
                    assertEquals(CREATED_AT, result.createdAt());
                    assertEquals(UPDATED_AT, result.updatedAt());
                });
    }
}
