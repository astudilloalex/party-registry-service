package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSelector;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.query.GetIdentifierSchemeQuery;
import com.alexastudillo.partyregistry.application.usecase.GetIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies detached global reads and compatible exact registration lookups against PostgreSQL. */
@QuarkusTest
@Timeout(60)
class HibernateReactiveIdentifierSchemeReadAdapterTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00.123456Z");

    @Inject
    IdentifierSchemeReadPort reader;
    @Inject
    IdentifierSchemeRepository registration;
    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    IdentifierSchemePersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void retrievesEveryStateAndHistoricalKeysWithoutAdmissionOrTenantFiltering(UniAsserter asserter) {
        List<IdentifierScheme> schemes = java.util.Arrays.stream(IdentifierSchemeStatus.values())
                .map(status -> scheme(status, status == IdentifierSchemeStatus.DRAFT)).toList();
        asserter.execute(() -> persist(schemes));
        var useCase = new GetIdentifierSchemeUseCase(reader);
        for (IdentifierScheme scheme : schemes) {
            var expected = IdentifierSchemeResult.fromAggregate(scheme);
            asserter.assertEquals(() -> timed(reader.findById(scheme.id())), Optional.of(expected));
            asserter.assertEquals(() -> timed(reader.findByCode(scheme.code())), Optional.of(expected));
            asserter.assertEquals(() -> timed(registration.findByCode(scheme.code())), Optional.of(scheme));
            for (int tenant = 0; tenant < 2; tenant++) {
                var metadata = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());
                for (IdentifierSchemeSelector selector : List.of(new IdentifierSchemeSelector.ById(scheme.id()),
                        new IdentifierSchemeSelector.ByCode(scheme.code()))) {
                    asserter.assertEquals(() -> timed(useCase.execute(new GetIdentifierSchemeQuery(metadata, selector))), expected);
                }
            }
            asserter.assertEquals(() -> timed(sessions.withSession(session ->
                    session.find(IdentifierSchemeEntity.class, scheme.id().value()).map(mapper::toDomain))), scheme);
        }
        asserter.surroundWith(work -> work.eventually(() -> delete(schemes)));
    }

    @Test
    @RunOnVertxContext
    void returnsExplicitAbsenceAndMatchesCaseAndWhitespaceExactly(UniAsserter asserter) {
        var scheme = scheme(IdentifierSchemeStatus.RETIRED, false);
        var lower = new IdentifierScheme(new IdentifierSchemeId(UUID.randomUUID()), scheme.code().toLowerCase(Locale.ROOT),
                scheme.issuingCountryCode(), scheme.category(), scheme.applicableSubjectType(), scheme.name(),
                scheme.description(), scheme.normalizerKey(), scheme.validatorKey(), scheme.minimumLength(),
                scheme.maximumLength(), scheme.requiresExpiration(), scheme.status(), scheme.version(), scheme.auditInfo());
        var schemes = List.of(scheme, lower);
        asserter.execute(() -> persist(schemes));
        for (IdentifierScheme exact : schemes) {
            asserter.assertEquals(() -> timed(reader.findByCode(exact.code())),
                    Optional.of(IdentifierSchemeResult.fromAggregate(exact)));
            asserter.assertEquals(() -> timed(registration.findByCode(exact.code())), Optional.of(exact));
        }
        for (String absent : List.of(scheme.code().trim(), scheme.code().toUpperCase(Locale.ROOT), "Unknown-" + UUID.randomUUID())) {
            asserter.assertThat(() -> timed(reader.findByCode(absent)), result -> assertTrue(result.isEmpty()));
            asserter.assertThat(() -> timed(registration.findByCode(absent)), result -> assertTrue(result.isEmpty()));
        }
        asserter.assertEquals(() -> timed(reader.findById(new IdentifierSchemeId(UUID.randomUUID()))), Optional.empty());
        asserter.assertThat(() -> timed(reader.findById(scheme.id())), result ->
                assertEquals(scheme, result.orElseThrow().toAggregate()));
        asserter.surroundWith(work -> work.eventually(() -> delete(schemes)));
    }

    private Uni<Void> persist(List<IdentifierScheme> schemes) {
        return timed(sessions.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (IdentifierScheme scheme : schemes) {
                sequence = sequence.call(() -> session.persist(mapper.toEntity(scheme)));
            }
            return sequence;
        }));
    }

    private Uni<Void> delete(List<IdentifierScheme> schemes) {
        return timed(sessions.withTransaction((session, transaction) -> session.createMutationQuery(
                "delete from IdentifierSchemeEntity where id in :ids")
                .setParameter("ids", schemes.stream().map(scheme -> scheme.id().value()).toList())
                .executeUpdate().replaceWithVoid()));
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, boolean nullable) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.randomUUID()), " Mixed-" + UUID.randomUUID() + " ", "GB",
                IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, " Exact Name ", nullable ? null : "Exact description",
                "HISTORICAL_NORMALIZER_V1", "HISTORICAL_VALIDATOR_V1", nullable ? null : 1, nullable ? null : 32767,
                !nullable, status, new IdentifierSchemeVersion(17),
                new AuditInfo(CREATED, "original-creator", CREATED.plusSeconds(60), "last-editor"));
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
