package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadContext;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.policy.StandardIdentifierNormalizer;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies actual protected historical lookup, qualified ownership and bounded multi-batch literal prefix navigation. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchReadAdapterTest {
    @Inject HibernateReactiveIdentifierSearchReadAdapter reads;
    @Inject Mutiny.SessionFactory sessions;
    @Inject JcaIdentifierProtectionAdapter protection;

    @Test
    @RunOnVertxContext
    void exactReturnsCompleteSafeHistoryWithoutDecryptionOrPartyDeduplication(UniAsserter asserter) {
        TenantId tenant = new TenantId(UUID.randomUUID());
        var scheme = new AtomicReference<IdentifierScheme>();
        var escaped = new AtomicReference<IdentifierSearchReadContext>();
        asserter.execute(() -> reads.withRead(tenant, view -> view.findSchemeByCode("EC_NATIONAL_ID").invoke(value -> scheme.set(value.orElseThrow()))));
        asserter.execute(() -> seed(tenant, scheme.get().id(), 201, false));
        asserter.execute(() -> sessions.withTransaction(session -> session.createNativeMutationQuery(
                "update party_identifiers set encrypted_value = 'v1.CORRUPT' where tenant_id = :tenant")
                .setParameter("tenant", tenant.value()).executeUpdate()));
        Instant tied = Instant.parse("2020-01-01T00:00:00.123456Z");
        asserter.execute(() -> tieCreationTimes(tenant, tied));
        asserter.assertThat(() -> reads.withRead(tenant, view -> {
            escaped.set(view);
            return view.findExact(scheme.get().id(), selector(" exact "), IdentifierSearchFilters.of(null, null));
        }), slice -> {
            assertEquals(201, slice.totalElements());
            assertEquals(201, slice.items().size());
            assertEquals(slice.items().getFirst().partyId(), slice.items().getLast().partyId());
            assertEquals("Stored Label", slice.items().getFirst().displayName());
            assertEquals(PartyRecordStatus.ARCHIVED, slice.items().getFirst().recordStatus());
            assertEquals(PartyIdentifierStatus.REVOKED, slice.items().getFirst().status());
            assertEquals(tied, slice.items().getFirst().position().createdAt());
            for (int i = 1; i < slice.items().size(); i++) {
                assertTrue(IdentifierSearchPositions.compare(slice.items().get(i - 1).position(), slice.items().get(i).position()) > 0);
            }
        });
        asserter.assertFailedWith(() -> escaped.get().findSchemeByCode("EC_NATIONAL_ID"), IllegalStateException.class);
        asserter.assertEquals(() -> reads.withRead(new TenantId(UUID.randomUUID()), view -> view.findExact(scheme.get().id(),
                selector("EXACT"), IdentifierSearchFilters.of(null, null))).map(ExactIdentifierSearchSlice::totalElements), 0L);
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findExact(scheme.get().id(), selector("EXACT"),
                IdentifierSearchFilters.of(List.of(PartyIdentifierStatus.VERIFIED), null))).map(ExactIdentifierSearchSlice::totalElements), 0L);
    }

    @Test
    @RunOnVertxContext
    void prefixAdvancesAcrossNonmatchesAndRestoresNearestPriorPageWithCompleteTotals(UniAsserter asserter) {
        TenantId tenant = new TenantId(UUID.randomUUID());
        var scheme = new AtomicReference<IdentifierScheme>();
        var first = new AtomicReference<PrefixIdentifierSearchSlice>();
        var second = new AtomicReference<PrefixIdentifierSearchSlice>();
        asserter.execute(() -> reads.withRead(tenant, view -> view.findSchemeByCode("EC_NATIONAL_ID").invoke(value -> scheme.set(value.orElseThrow()))));
        asserter.execute(() -> seed(tenant, scheme.get().id(), 520, true));
        asserter.assertThat(() -> prefix(tenant, scheme.get(), Optional.empty()), slice -> {
            assertEquals(260, slice.totalElements());
            assertEquals(2, slice.items().size());
            assertTrue(slice.previous().isEmpty());
            first.set(slice);
        });
        asserter.assertThat(() -> prefix(tenant, scheme.get(), Optional.of(new PartyIdentifierPageBoundary(
                PartyIdentifierPageBoundary.Direction.NEXT, first.get().next().orElseThrow()))), slice -> {
            assertEquals(260, slice.totalElements());
            assertEquals(2, slice.items().size());
            assertNotEquals(first.get().items(), slice.items());
            second.set(slice);
        });
        asserter.assertThat(() -> prefix(tenant, scheme.get(), Optional.of(new PartyIdentifierPageBoundary(
                PartyIdentifierPageBoundary.Direction.PREVIOUS, second.get().previous().orElseThrow()))),
                slice -> assertEquals(first.get().items(), slice.items()));
    }

    private Uni<PrefixIdentifierSearchSlice> prefix(TenantId tenant, IdentifierScheme scheme, Optional<PartyIdentifierPageBoundary> boundary) {
        var scope = new IdentifierSearchScope(tenant, scheme.id(), scheme.code(), selector("literal%_"), IdentifierSearchFilters.of(null, null), 2);
        return reads.withRead(tenant, view -> view.findPrefix(scope, boundary));
    }

    @Test
    @RunOnVertxContext
    void prefixKeysetsRetainDatabasePrecisionAcrossTimestampTiesAndMultipleBatches(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var scheme = new AtomicReference<IdentifierScheme>();
        var first = new AtomicReference<PrefixIdentifierSearchSlice>();
        Instant tied = Instant.parse("2020-01-01T00:00:00.123456Z");
        asserter.execute(() -> reads.withRead(tenant, view -> view.findSchemeByCode("EC_NATIONAL_ID").invoke(value -> scheme.set(value.orElseThrow()))));
        asserter.execute(() -> seed(tenant, scheme.get().id(), 270, true));
        asserter.execute(() -> tieCreationTimes(tenant, tied));
        asserter.assertThat(() -> prefix(tenant, scheme.get(), Optional.empty()), slice -> {
            first.set(slice);
            assertEquals(260, slice.totalElements());
            assertEquals(tied, slice.items().getFirst().position().createdAt());
            assertTrue(IdentifierSearchPositions.compare(slice.items().getFirst().position(), slice.items().getLast().position()) > 0);
        });
        asserter.assertThat(() -> prefix(tenant, scheme.get(), Optional.of(new PartyIdentifierPageBoundary(
                PartyIdentifierPageBoundary.Direction.NEXT, first.get().next().orElseThrow()))), slice -> {
            assertEquals(260, slice.totalElements());
            assertTrue(IdentifierSearchPositions.compare(first.get().items().getLast().position(), slice.items().getFirst().position()) > 0);
        });
    }

    @Test
    @RunOnVertxContext
    void filtersBeforeDecryptionAndFailsRequiredCorruptCandidatesWithoutSkippingThem(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var scheme = new AtomicReference<IdentifierScheme>();
        asserter.execute(() -> reads.withRead(tenant, view -> view.findSchemeByCode("EC_NATIONAL_ID").invoke(value -> scheme.set(value.orElseThrow()))));
        asserter.execute(() -> seed(tenant, scheme.get().id(), 1, true));
        asserter.execute(() -> sessions.withTransaction(session -> session.createNativeMutationQuery(
                "update party_identifiers set encrypted_value = 'v1.CORRUPT' where tenant_id = :tenant")
                .setParameter("tenant", tenant.value()).executeUpdate()));
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findPrefix(new IdentifierSearchScope(tenant, scheme.get().id(),
                scheme.get().code(), selector("LITERAL%_"), IdentifierSearchFilters.of(List.of(PartyIdentifierStatus.VERIFIED), null), 2),
                Optional.empty())).map(PrefixIdentifierSearchSlice::totalElements), 0L);
        asserter.assertFailedWith(() -> prefix(tenant, scheme.get(), Optional.empty()), failure -> {
            assertInstanceOf(IllegalStateException.class, failure);
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("CORRUPT"));
        });
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findExact(scheme.get().id(), selector("LITERAL%_0"),
                IdentifierSearchFilters.of(null, null))).map(ExactIdentifierSearchSlice::totalElements), 1L);
    }

    private Uni<Void> tieCreationTimes(TenantId tenant, Instant created) {
        return sessions.withTransaction(session -> session.createNativeMutationQuery(
                "update party_identifiers set created_at = :created where tenant_id = :tenant")
                .setParameter("created", created).setParameter("tenant", tenant.value()).executeUpdate()).replaceWithVoid();
    }

    @Test
    @RunOnVertxContext
    void schemeResolutionAndRepeatedReadsShareOneViewWhileLaterRequestsAreLive(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var id = new IdentifierSchemeId(UUID.randomUUID());
        String code = "SEARCH_" + UUID.randomUUID();
        var original = IdentifierScheme.create(id, code, "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Original", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 1, 256, false, Instant.EPOCH, "test");
        var mapper = new IdentifierSchemePersistenceMapper();
        asserter.execute(() -> sessions.withTransaction(session -> session.persist(mapper.toEntity(original))));
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findSchemeByCode(code).chain(first ->
                sessions.withTransaction(writer -> writer.createNativeMutationQuery(
                        "update identifier_schemes set name = 'Changed', version = version + 1 where id = :id")
                        .setParameter("id", id.value()).executeUpdate())
                .chain(() -> view.findSchemeByCode(code)).map(second -> {
                    assertEquals(first.orElseThrow().version(), second.orElseThrow().version());
                    return second.orElseThrow().name();
                }))), "Original");
        asserter.assertEquals(() -> reads.withRead(tenant, view -> view.findSchemeByCode(code).map(value -> value.orElseThrow().name())), "Changed");
    }

    private Uni<Void> seed(TenantId tenant, IdentifierSchemeId scheme, int count, boolean prefix) {
        UUID party = UUID.randomUUID();
        return sessions.withTransaction(session -> session.createNativeMutationQuery("""
                INSERT INTO parties (id, tenant_id, type, display_name, record_status, created_by, updated_by)
                VALUES (:party, :tenant, 'NATURAL_PERSON', 'Stored Label', 'ARCHIVED', 'test', 'test')
                """).setParameter("party", party).setParameter("tenant", tenant.value()).executeUpdate()
                .chain(() -> session.createNativeMutationQuery("""
                        INSERT INTO natural_person_details (party_id, given_names, family_names, created_by, updated_by)
                        VALUES (:party, 'Synthetic', 'Fixture', 'test', 'test')
                        """).setParameter("party", party).executeUpdate())
                .chain(() -> io.smallrye.mutiny.Multi.createFrom().range(0, count).onItem().transformToUniAndConcatenate(index -> {
                    var id = new PartyIdentifierId(UUID.randomUUID());
                    // Newest 260 rows do not match, requiring traversal advancement using candidate rather than match positions.
                    String value = prefix ? (index < 260 ? "LITERAL%_" : "OTHER-") + index : "EXACT";
                    var stored = protection.protect(new IdentifierProtectionRequest(tenant, new PartyId(party), id, scheme, value, value, new IdentifierRuleVersion(1)));
                    return session.createNativeMutationQuery("""
                            INSERT INTO party_identifiers (id, tenant_id, party_id, identifier_scheme_id, encrypted_value,
                                encryption_key_version, normalized_value_hash, masked_value, normalization_version,
                                status, is_primary, created_at, created_by, updated_by)
                            VALUES (:id, :tenant, :party, :scheme, :cipher, :key, :hash, :mask, 1,
                                'REVOKED', false, :created, 'test', 'test')
                            """).setParameter("id", id.value()).setParameter("tenant", tenant.value()).setParameter("party", party)
                            .setParameter("scheme", scheme.value()).setParameter("cipher", stored.encryptedValue())
                            .setParameter("key", stored.encryptionKeyVersion()).setParameter("hash", stored.normalizedValueHash())
                            .setParameter("mask", stored.maskedValue()).setParameter("created", Instant.EPOCH.plusSeconds(index)).executeUpdate();
                }).collect().last()).replaceWithVoid());
    }

    private static IdentifierSearchSelector selector(String raw) {
        var normalizer = StandardIdentifierNormalizer.TRIM_UPPERCASE_V1;
        return new IdentifierSearchSelector(normalizer, normalizer.normalize(raw));
    }
}
