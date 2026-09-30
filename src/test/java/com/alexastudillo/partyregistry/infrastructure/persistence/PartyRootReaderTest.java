package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies safe root reads for both historical subtypes, every lifecycle state, tenant concealment, and corrupt data.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class PartyRootReaderTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC);

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    PartyRootReader reader;
    @Inject
    NaturalPersonPersistenceMapper naturalMapper;
    @Inject
    LegalEntityPersistenceMapper legalMapper;

    @Test
    @RunOnVertxContext
    void readsBothSubtypesInEveryStateWithoutRewritingStoredValues(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        List<Party> parties = new ArrayList<>();
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                parties.add(fixture(tenant, type, status));
            }
        }
        asserter.execute(() -> persist(parties));
        for (Party party : parties) {
            asserter.assertThat(() -> timed(reader.findDetails(tenant, party.partyId())),
                    result -> assertEquals(PartyDetailsResult.fromAggregate(party), result.orElseThrow()));
        }
    }

    @Test
    @RunOnVertxContext
    void concealsMissingAndCrossTenantIdentitiesEqually(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var otherTenant = new TenantId(UUID.randomUUID());
        Party party = fixture(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
        var missingId = new PartyId(UUID.randomUUID());
        asserter.execute(() -> persist(List.of(party)));
        asserter.assertThat(() -> timed(reader.findDetails(otherTenant, party.partyId())),
                result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> timed(reader.findDetails(tenant, missingId)),
                result -> assertTrue(result.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void reportsAMissingDetailAsCorruptStateInsteadOfAbsenceOrPartialSuccess(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var partyId = new PartyId(UUID.randomUUID());
        var root = new PartyEntity(partyId.value(), tenant.value(), PartyType.NATURAL_PERSON,
                "Corrupt Fixture", PartyRecordStatus.DRAFT, AuditInfo.initial(CREATED, "fixture"), 0);
        asserter.execute(() -> timed(sessionFactory.withTransaction((session, transaction) -> session.persist(root))));
        asserter.assertFailedWith(() -> timed(reader.findDetails(tenant, partyId)), failure -> {
            var application = assertInstanceOf(ApplicationException.class, failure);
            assertInstanceOf(ApplicationFailure.PersistenceFailure.class, application.failure());
            assertEquals("Persistence operation failed", application.getMessage());
        });
    }

    private Uni<Void> persist(List<Party> parties) {
        return timed(sessionFactory.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (Party party : parties) {
                PartyEntity entity = switch (party) {
                    case NaturalPerson natural -> naturalMapper.toEntity(natural);
                    case LegalEntity legal -> legalMapper.toEntity(legal);
                };
                sequence = sequence.call(() -> session.persist(entity));
            }
            return sequence;
        }));
    }

    private static Party fixture(TenantId tenant, PartyType type, PartyRecordStatus status) {
        var partyId = new PartyId(UUID.randomUUID());
        var audit = AuditInfo.initial(CREATED, "historical-creator");
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(partyId, tenant, " Historical Áda ", status,
                    PartyVersion.initial(), audit, new NaturalPersonDetails("Mixed", "Case", " Ada ",
                            LocalDate.of(1990, Month.JANUARY, 1), null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(partyId, tenant, " Historical Company ", status,
                    PartyVersion.initial(), audit, new LegalEntityDetails("Mixed Company", " Trade ", "Ltd", "GB",
                            LocalDate.of(2020, Month.JANUARY, 1), null));
        };
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
