package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageSlice;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierQueryPort;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.infrastructure.observability.PartyPersistenceObservability;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provides safe historical detail and bounded count/page/navigation queries from a consistent reactive snapshot. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyIdentifierQueryAdapter implements PartyIdentifierQueryPort {

    static final String SNAPSHOT_TRANSACTION = "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY";
    private final Mutiny.SessionFactory sessions;
    private final PartyIdentifierDetailReader detailReader;
    private final PartyQueryTimeouts timeouts;
    private final PartyPersistenceObservability observation;

    public HibernateReactivePartyIdentifierQueryAdapter(Mutiny.SessionFactory sessions,
            PartyIdentifierDetailReader detailReader, PartyQueryTimeouts timeouts) {
        this(sessions, detailReader, timeouts, null);
    }

    /** Composes consistent page reads and managed bounded page-cost observation. */
    @Inject
    public HibernateReactivePartyIdentifierQueryAdapter(Mutiny.SessionFactory sessions,
            PartyIdentifierDetailReader detailReader, PartyQueryTimeouts timeouts, PartyPersistenceObservability observation) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.detailReader = Objects.requireNonNull(detailReader, "detailReader");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.observation = observation;
    }

    @Override
    public Uni<Optional<PartyIdentifierResult>> findById(RequestMetadata metadata, PartyId partyId,
            PartyIdentifierId identifierId) {
        return detailReader.findById(metadata, partyId, identifierId);
    }

    @Override
    public Uni<PartyIdentifierPageSlice> findPage(RequestMetadata metadata, PartyIdentifierSearchScope scope,
            @Nullable PartyIdentifierPageBoundary boundary) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(scope, "scope");
        if (!metadata.tenantId().equals(scope.tenantId())) {
            throw new IllegalArgumentException("Identifier page scope does not match accepted tenant");
        }
        return observation == null ? read(scope, boundary) : observation.observeIdentifierPage(() -> read(scope, boundary));
    }

    private Uni<PartyIdentifierPageSlice> read(PartyIdentifierSearchScope scope, @Nullable PartyIdentifierPageBoundary boundary) {
        return sessions.withTransaction((session, transaction) -> {
            session.setDefaultReadOnly(true);
            return timeouts.limit(session.createNativeQuery(SNAPSHOT_TRANSACTION).executeUpdate()
                    .call(() -> timeouts.configure(session))
                    .flatMap(ignored -> readPage(session, scope, boundary)));
        }).onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    private static Uni<PartyIdentifierPageSlice> readPage(Mutiny.Session session, PartyIdentifierSearchScope scope,
            @Nullable PartyIdentifierPageBoundary boundary) {
        var sql = new PartyIdentifierPageSql(scope);
        return sql.count(session).flatMap(total -> {
            long count = Objects.requireNonNull(total, "identifier count");
            if (count == 0) {
                return Uni.createFrom().item(new PartyIdentifierPageSlice(List.of(), 0, Optional.empty(), Optional.empty()));
            }
            return sql.page(session, boundary).flatMap(items -> navigation(session, sql, items, count, boundary));
        });
    }

    private static Uni<PartyIdentifierPageSlice> navigation(Mutiny.Session session, PartyIdentifierPageSql sql,
            List<PartyIdentifierResult> items, long total, @Nullable PartyIdentifierPageBoundary boundary) {
        PartyIdentifierPagePosition next;
        PartyIdentifierPagePosition previous;
        if (items.isEmpty()) {
            if (boundary == null) {
                throw new IllegalStateException("Identifier count and initial page are inconsistent");
            }
            next = boundary.position();
            previous = next;
        } else {
            next = position(items.getLast());
            previous = position(items.getFirst());
        }
        return sql.exists(session, new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, next))
                .flatMap(hasNext -> sql.exists(session,
                        new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, previous))
                        .map(hasPrevious -> new PartyIdentifierPageSlice(items, total,
                                Boolean.TRUE.equals(hasNext) ? Optional.of(next) : Optional.empty(),
                                Boolean.TRUE.equals(hasPrevious) ? Optional.of(previous) : Optional.empty())));
    }

    private static PartyIdentifierPagePosition position(PartyIdentifierResult result) {
        return new PartyIdentifierPagePosition(result.createdAt(), result.identifierId());
    }
}
