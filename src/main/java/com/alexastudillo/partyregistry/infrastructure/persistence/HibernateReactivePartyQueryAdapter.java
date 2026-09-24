package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.observability.PartyPersistenceObservability;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts common Party reads and bounded snapshot-consistent summary queries to reactive persistence.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyQueryAdapter implements PartyQueryPort {

    static final int NAME_SCAN_BATCH_SIZE = 256;
    private final Mutiny.SessionFactory sessionFactory;
    private final PartyRootReader rootReader;
    private final PartyQueryTimeouts timeouts;
    private final PartyPersistenceObservability observations;

    /** Creates a per-subscription snapshot reader without retaining sessions in the bean. */
    @Inject
    public HibernateReactivePartyQueryAdapter(Mutiny.SessionFactory sessionFactory, PartyRootReader rootReader,
            PartyQueryTimeouts timeouts, PartyPersistenceObservability observations) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.rootReader = Objects.requireNonNull(rootReader, "rootReader");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    @Override
    public Uni<Optional<PartyDetailsResult>> findDetails(TenantId tenantId, PartyId partyId) {
        return rootReader.findDetails(tenantId, partyId);
    }

    @Override
    public Uni<PartyPageSlice> findPage(TenantId tenant, PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(boundary, "boundary");
        return sessionFactory.withTransaction((session, transaction) -> {
            session.setDefaultReadOnly(true);
            return timeouts.limit(session.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
                    .executeUpdate().call(() -> timeouts.configure(session))
                    .flatMap(ignored -> criteria.name().isEffective()
                            ? readNamePage(session, tenant, criteria, boundary)
                            : readStructuralPage(session, tenant, criteria, boundary)));
        }).onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    private Uni<PartyPageSlice> readNamePage(Mutiny.Session session, TenantId tenant,
            PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary) {
        return observations.observeScan(statistics -> {
            var sql = new PartyPageSql(tenant, criteria);
            var accumulator = new PartyNamePageAccumulator(criteria, boundary);
            return Multi.createBy().repeating()
                    .uni(() -> sql.summaries(session, accumulator.scanBoundary().orElse(null), NAME_SCAN_BATCH_SIZE))
                    .until(List::isEmpty)
                    .invoke(batch -> statistics.receivedBatch(batch.size()))
                    .invoke(accumulator::acceptBatch)
                    .collect().last().replaceWith(accumulator::result);
        });
    }

    private static Uni<PartyPageSlice> readStructuralPage(Mutiny.Session session, TenantId tenant,
            PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary) {
        var sql = new PartyPageSql(tenant, criteria);
        return sql.count(session).flatMap(total -> {
            if (total == 0) {
                return Uni.createFrom().item(new PartyPageSlice(List.of(), 0, Optional.empty(), Optional.empty()));
            }
            return sql.summaries(session, boundary.orElse(null), criteria.limit())
                    .flatMap(items -> navigation(session, sql, items, total, boundary));
        });
    }

    private static Uni<PartyPageSlice> navigation(Mutiny.Session session, PartyPageSql sql,
            List<PartySummaryResult> items, long total, Optional<PartyPageBoundary> requested) {
        PartyPagePosition next = items.isEmpty() ? requested.orElseThrow().position() : items.getLast().position();
        PartyPagePosition previous = items.isEmpty() ? requested.orElseThrow().position() : items.getFirst().position();
        return sql.exists(session, new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, next))
                .flatMap(hasNext -> {
                    boolean nextAvailable = Objects.requireNonNull(hasNext, "next availability");
                    return sql.exists(session, new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, previous))
                            .map(hasPrevious -> {
                                boolean previousAvailable = Objects.requireNonNull(hasPrevious, "previous availability");
                                return new PartyPageSlice(items, total,
                                        nextAvailable ? Optional.of(next) : Optional.empty(),
                                        previousAvailable ? Optional.of(previous) : Optional.empty());
                            });
                });
    }
}
