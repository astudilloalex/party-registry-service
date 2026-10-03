package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies reactive ORM mappings and compatibility with the retained activated-identity trigger. */
@QuarkusTest
@Timeout(60)
class IdentifierSchemeEntityPersistenceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T10:00:00.123456Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    @Inject
    Mutiny.SessionFactory sessions;

    @Inject
    IdentifierSchemePersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void roundTripsSchemeAndDedicatedReplayJsonThroughPostgres(UniAsserter asserter) {
        for (Integer bound : new Integer[]{null, 1, 32767}) {
            IdentifierScheme original = scheme(IdentifierSchemeStatus.DRAFT, bound);
            var identity = new IdentifierSchemeIdempotencyRecordId(UUID.randomUUID(), "identifier-scheme.create.v1", " Exact-Key ");
            var snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.putObject("result").put("id", original.id().value().toString()).put("version", 0);
            snapshot.putObject("input").putNull("description").put("maximumLength", 32767);
            var replay = new IdentifierSchemeIdempotencyRecordEntity(identity, "b".repeat(64), original.id().value(),
                    (short) 1, snapshot, CREATED_AT, "original-actor");
            asserter.assertThat(() -> timed(sessions.withTransaction((session, transaction) -> {
                transaction.markForRollback();
                return session.persist(mapper.toEntity(original))
                        .call(() -> session.persist(replay)).call(session::flush)
                        .invoke(session::clear)
                        .flatMap(ignored -> session.find(IdentifierSchemeEntity.class, original.id().value()))
                        .invoke(restored -> assertEquals(original, mapper.toDomain(restored)))
                        .flatMap(ignored -> session.find(IdentifierSchemeIdempotencyRecordEntity.class,
                                new IdentifierSchemeIdempotencyRecordId(identity.tenantId(), identity.operation(), identity.idempotencyKey())));
            })), restored -> {
                assertNotNull(restored);
                assertEquals(identity, restored.id());
                assertEquals("b".repeat(64), restored.requestHash());
                assertEquals(original.id().value(), restored.identifierSchemeId());
                assertEquals(1, restored.resultSnapshotSchemaVersion());
                assertEquals(snapshot, restored.resultSnapshot());
                assertEquals(CREATED_AT, restored.createdAt());
                assertEquals("original-actor", restored.createdBy());
            });
        }
    }

    @Test
    @RunOnVertxContext
    void permitsDescriptiveAndLifecycleUpdatesWithIdenticalIdentityInEveryActivatedState(UniAsserter asserter) {
        for (IdentifierSchemeStatus status : new IdentifierSchemeStatus[]{IdentifierSchemeStatus.ACTIVE,
                IdentifierSchemeStatus.DEPRECATED, IdentifierSchemeStatus.RETIRED}) {
            IdentifierScheme original = scheme(status, 32767);
            asserter.assertThat(() -> timed(sessions.withTransaction((session, transaction) -> {
                transaction.markForRollback();
                return session.persist(mapper.toEntity(original)).call(session::flush)
                        .flatMap(ignored -> session.createNativeQuery("""
                                UPDATE identifier_schemes SET code = code, issuing_country_code = issuing_country_code,
                                    category = category, applicable_subject_type = applicable_subject_type,
                                    name = 'Updated name', description = NULL, status = 'RETIRED', version = version + 1,
                                    updated_at = :at, updated_by = 'editor' WHERE id = :id
                                """).setParameter("at", UPDATED_AT).setParameter("id", original.id().value()).executeUpdate())
                        .invoke(count -> assertEquals(1, count)).invoke(session::clear)
                        .flatMap(ignored -> session.find(IdentifierSchemeEntity.class, original.id().value()));
            })), restored -> {
                var result = mapper.toDomain(restored);
                assertEquals(original.id(), result.id());
                assertEquals(original.code(), result.code());
                assertEquals(original.issuingCountryCode(), result.issuingCountryCode());
                assertEquals(original.category(), result.category());
                assertEquals(original.applicableSubjectType(), result.applicableSubjectType());
                assertEquals(original.normalizerKey(), result.normalizerKey());
                assertEquals(original.validatorKey(), result.validatorKey());
                assertEquals(original.minimumLength(), result.minimumLength());
                assertEquals(original.maximumLength(), result.maximumLength());
                assertEquals(original.requiresExpiration(), result.requiresExpiration());
                assertEquals("Updated name", result.name());
                assertEquals(IdentifierSchemeStatus.RETIRED, result.status());
                assertEquals(1, result.version().value());
                assertEquals(new AuditInfo(CREATED_AT, "creator", UPDATED_AT, "editor"), result.auditInfo());
            });
        }
    }

    @Test
    @RunOnVertxContext
    void triggerRejectsEveryIdentityChangeAfterActivation(UniAsserter asserter) {
        for (IdentifierSchemeStatus status : new IdentifierSchemeStatus[]{IdentifierSchemeStatus.ACTIVE,
                IdentifierSchemeStatus.DEPRECATED, IdentifierSchemeStatus.RETIRED}) {
            for (String assignment : new String[]{"code = 'Changed'", "issuing_country_code = 'US'",
                    "category = 'PASSPORT'", "applicable_subject_type = 'NATURAL_PERSON'"}) {
                IdentifierScheme original = scheme(status, null);
                asserter.assertFailedWith(() -> timed(sessions.withTransaction((session, transaction) ->
                        session.persist(mapper.toEntity(original)).call(session::flush)
                                .flatMap(ignored -> session.createNativeQuery(
                                        "UPDATE identifier_schemes SET " + assignment + " WHERE id = :id")
                                        .setParameter("id", original.id().value()).executeUpdate()))), failure -> {
                    var violation = assertInstanceOf(ConstraintViolationException.class, failure);
                    assertEquals("23514", violation.getSQLState());
                    // Hibernate Reactive retains SQLSTATE/message but discards this trigger's constraint metadata.
                    assertTrue(violation.getSQLException().getMessage()
                            .contains("Activated identifier scheme identity fields are immutable"));
                });
            }
        }
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, Integer bound) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.randomUUID()), " Exact-" + UUID.randomUUID(), "GB",
                IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Exact Name", null,
                "HISTORICAL_NORMALIZER_V1", "HISTORICAL_VALIDATOR_V1", bound, bound, true, status,
                IdentifierSchemeVersion.initial(), AuditInfo.initial(CREATED_AT, "creator"));
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
