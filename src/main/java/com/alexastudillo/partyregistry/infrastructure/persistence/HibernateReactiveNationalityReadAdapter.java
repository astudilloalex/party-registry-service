package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPagePosition;
import com.alexastudillo.partyregistry.application.model.NationalityPageSlice;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reads Party ownership and nationality detail with qualified reactive SQL and
 * detached results.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveNationalityReadAdapter implements NationalityReadPort {

        private static final String CHECK_PARTY_EXISTS = """
                        select party.id from PartyEntity party
                        where party.tenantId = :tenantId and party.id = :partyId
                        """;
        private static final String FIND_BY_ID = """
                        select nationality from PartyNationalityEntity nationality
                        join PartyEntity party on party.id = nationality.partyId
                        where party.tenantId = :tenantId and party.id = :partyId
                          and nationality.id = :nationalityId
                        """;
        private static final String TENANT_ID = "tenantId";
        private static final String PARTY_ID = "partyId";
        private static final String NATIONALITY_ID = "nationalityId";

        private final Mutiny.SessionFactory sessionFactory;
        private final PartyQueryTimeouts timeouts;

        @Inject
        public HibernateReactiveNationalityReadAdapter(Mutiny.SessionFactory sessionFactory,
                        PartyQueryTimeouts timeouts) {
                this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
                this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        }

        @Override
        public Uni<Boolean> partyExists(TenantId tenantId, PartyId partyId) {
                Objects.requireNonNull(tenantId, TENANT_ID);
                Objects.requireNonNull(partyId, PARTY_ID);
                return readOnly(session -> session.createQuery(CHECK_PARTY_EXISTS, UUID.class)
                                .setParameter(PARTY_ID, partyId.value())
                                .setParameter(TENANT_ID, tenantId.value())
                                .getSingleResultOrNull()
                                .map(Objects::nonNull));
        }

        @Override
        public Uni<Optional<NationalityResult>> findById(
                        TenantId tenantId, PartyId partyId, NationalityId nationalityId) {
                Objects.requireNonNull(tenantId, TENANT_ID);
                Objects.requireNonNull(partyId, PARTY_ID);
                Objects.requireNonNull(nationalityId, NATIONALITY_ID);
                return readOnly(session -> session.createQuery(FIND_BY_ID, PartyNationalityEntity.class)
                                .setParameter(TENANT_ID, tenantId.value())
                                .setParameter(PARTY_ID, partyId.value())
                                .setParameter(NATIONALITY_ID, nationalityId.value())
                                .getSingleResultOrNull()
                                .map(entity -> entity == null ? Optional.empty() : Optional.of(entity.toResult())));
        }

        @Override
        public Uni<Optional<NationalityPageSlice>> findPage(TenantId tenantId, PartyId partyId,
                        NationalitySearchCriteria criteria, Optional<NationalityPageBoundary> boundary) {
                Objects.requireNonNull(tenantId, TENANT_ID);
                Objects.requireNonNull(partyId, PARTY_ID);
                Objects.requireNonNull(criteria, "criteria");
                Objects.requireNonNull(boundary, "boundary");
                return sessionFactory.withTransaction((session, transaction) -> {
                        session.setDefaultReadOnly(true);
                        return timeouts.limit(session
                                        .createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
                                        .executeUpdate().call(() -> timeouts.configure(session))
                                        .flatMap(ignored -> session.createQuery(CHECK_PARTY_EXISTS, UUID.class)
                                                        .setParameter(TENANT_ID, tenantId.value())
                                                        .setParameter(PARTY_ID, partyId.value())
                                                        .getSingleResultOrNull())
                                        .flatMap(owned -> owned == null
                                                        ? Uni.createFrom().item(Optional.<NationalityPageSlice>empty())
                                                        : page(session, tenantId, partyId, criteria, boundary)
                                                                        .map(Optional::of)));
                }).onFailure(PersistenceExceptionTranslator::requiresTranslation)
                                .transform(PersistenceExceptionTranslator::toApplicationException);
        }

        private static Uni<NationalityPageSlice> page(Mutiny.Session session, TenantId tenantId, PartyId partyId,
                        NationalitySearchCriteria criteria, Optional<NationalityPageBoundary> boundary) {
                var sql = new NationalityPageSql(tenantId, partyId, criteria);
                return sql.count(session).flatMap(total -> {
                        if (total == 0) {
                                return Uni.createFrom().item(new NationalityPageSlice(List.of(), 0, Optional.empty(),
                                                Optional.empty()));
                        }
                        return sql.page(session, boundary.orElse(null), criteria.limit())
                                        .flatMap(items -> navigation(session, sql, items, total, boundary));
                });
        }

        private static Uni<NationalityPageSlice> navigation(Mutiny.Session session, NationalityPageSql sql,
                        List<NationalityResult> items, long total, Optional<NationalityPageBoundary> requested) {
                NationalityPagePosition last = items.isEmpty() ? requested.orElseThrow().position()
                                : position(items.getLast());
                NationalityPagePosition first = items.isEmpty() ? requested.orElseThrow().position()
                                : position(items.getFirst());
                return sql.exists(session, new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT, last))
                                .flatMap(hasNext -> {
                                        boolean nextAvailable = Objects.requireNonNull(hasNext, "next availability");
                                        return sql.exists(session,
                                                        new NationalityPageBoundary(
                                                                        NationalityPageBoundary.Direction.PREVIOUS,
                                                                        first))
                                                        .map(hasPrevious -> {
                                                                boolean previousAvailable = Objects.requireNonNull(
                                                                                hasPrevious, "previous availability");
                                                                return new NationalityPageSlice(items, total,
                                                                                nextAvailable ? Optional.of(last)
                                                                                                : Optional.empty(),
                                                                                previousAvailable ? Optional.of(first)
                                                                                                : Optional.empty());
                                                        });
                                });
        }

        private static NationalityPagePosition position(NationalityResult result) {
                return new NationalityPagePosition(result.createdAt(), result.nationalityId());
        }

        private <T> Uni<T> readOnly(Function<Mutiny.Session, Uni<T>> query) {
                return sessionFactory.withTransaction((session, transaction) -> {
                        session.setDefaultReadOnly(true);
                        return timeouts.limit(session.createNativeQuery("SET TRANSACTION READ ONLY").executeUpdate()
                                        .call(() -> timeouts.configure(session))
                                        .flatMap(ignored -> query.apply(session)));
                }).onFailure(PersistenceExceptionTranslator::requiresTranslation)
                                .transform(PersistenceExceptionTranslator::toApplicationException);
        }
}
