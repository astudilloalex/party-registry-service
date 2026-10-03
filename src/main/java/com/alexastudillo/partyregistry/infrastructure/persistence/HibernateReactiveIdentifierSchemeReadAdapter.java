package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Reads detached global catalog data inside bounded reactive read-only snapshots. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveIdentifierSchemeReadAdapter implements IdentifierSchemeReadPort {

    private final Mutiny.SessionFactory sessions;
    private final IdentifierSchemePersistenceMapper mapper;
    private final PartyQueryTimeouts timeouts;

    @Inject
    public HibernateReactiveIdentifierSchemeReadAdapter(Mutiny.SessionFactory sessions,
            IdentifierSchemePersistenceMapper mapper, PartyQueryTimeouts timeouts) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
    }

    @Override
    public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
        Objects.requireNonNull(id, "id");
        return readOnly(session -> session.createSelectionQuery(
                "from IdentifierSchemeEntity scheme where scheme.id = :id", IdentifierSchemeEntity.class)
                .setParameter("id", id.value()).getSingleResultOrNull().map(this::detached));
    }

    @Override
    public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return readOnly(session -> session.createSelectionQuery(
                "from IdentifierSchemeEntity scheme where scheme.code = :code", IdentifierSchemeEntity.class)
                .setParameter("code", code).getSingleResultOrNull().map(this::detached));
    }

    @Override
    public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria,
            Optional<IdentifierSchemePageBoundary> boundary) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(boundary, "boundary");
        return readOnly(session -> {
            var sql = new IdentifierSchemePageSql(criteria);
            boolean backward = isBackward(boundary);
            return sql.page(session, boundary.orElse(null))
                    .flatMap(rows -> toSlice(session, sql, rows, criteria.limit(), backward));
        });
    }

    private Uni<IdentifierSchemePageSlice> toSlice(Mutiny.Session session, IdentifierSchemePageSql sql,
            List<IdentifierSchemeEntity> rows, int limit, boolean backward) {
        if (rows.isEmpty()) {
            return Uni.createFrom().item(new IdentifierSchemePageSlice(List.of(), Optional.empty(), Optional.empty()));
        }
        var window = PageWindow.from(rows, limit, backward, mapper);
        return sql.exists(session, window.oppositeBoundary(backward))
                .map(exists -> window.toSlice(backward, exists));
    }

    private static boolean isBackward(Optional<IdentifierSchemePageBoundary> boundary) {
        return boundary.map(value -> value.direction() == IdentifierSchemePageBoundary.Direction.PREVIOUS)
                .orElse(false);
    }

    private static IdentifierSchemePagePosition position(IdentifierSchemeResult result) {
        return new IdentifierSchemePagePosition(result.createdAt(), result.id());
    }

    /**
     * Retains bounded items, pagination flags, and boundary positions for a queried keyset slice.
     */
    private record PageWindow(
            List<IdentifierSchemeResult> items,
            boolean more,
            IdentifierSchemePagePosition first,
            IdentifierSchemePagePosition last) {

        static PageWindow from(List<IdentifierSchemeEntity> rows, int limit, boolean backward,
                IdentifierSchemePersistenceMapper mapper) {
            boolean more = rows.size() > limit;
            var bounded = rows.subList(0, Math.min(rows.size(), limit));
            // The extra row is farther from the boundary and must be removed before reversing.
            var ordered = backward ? bounded.reversed() : bounded;
            var items = ordered.stream().map(mapper::toDomain).map(IdentifierSchemeResult::fromAggregate).toList();
            return new PageWindow(items, more, position(items.getFirst()), position(items.getLast()));
        }

        IdentifierSchemePageBoundary oppositeBoundary(boolean backward) {
            if (backward) {
                return new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT, last);
            }
            return new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.PREVIOUS, first);
        }

        IdentifierSchemePageSlice toSlice(boolean backward, boolean exists) {
            boolean hasNext = backward ? exists : more;
            boolean hasPrevious = backward ? more : exists;
            return new IdentifierSchemePageSlice(items, boundaryOf(hasNext, last), boundaryOf(hasPrevious, first));
        }

        private static Optional<IdentifierSchemePagePosition> boundaryOf(boolean available,
                IdentifierSchemePagePosition position) {
            return available ? Optional.of(position) : Optional.empty();
        }
    }

    private Optional<IdentifierSchemeResult> detached(IdentifierSchemeEntity entity) {
        return Optional.ofNullable(entity).map(mapper::toDomain).map(IdentifierSchemeResult::fromAggregate);
    }

    private <T> Uni<T> readOnly(Function<Mutiny.Session, Uni<T>> query) {
        return IdentifierSchemeAtomicSession.execute(sessions, session -> {
            session.setDefaultReadOnly(true);
            return timeouts.limit(session.createNativeQuery(
                    "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY").executeUpdate()
                    .call(() -> timeouts.configure(session)).flatMap(ignored -> query.apply(session)));
        })
                .onFailure().transform(IdentifierSchemePersistenceFailures::translate);
    }
}
