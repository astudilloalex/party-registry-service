package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds bound structural predicates and summary-only keyset queries with fixed, unsigned PostgreSQL UUID order.
 */
final class PartyPageSql {

    private static final String SUMMARY = "select party.id, party.type, party.displayName, "
            + "party.recordStatus, party.createdAt, party.version";
    private final String where;
    private final Map<String, Object> parameters;

    PartyPageSql(TenantId tenant, PartySearchCriteria criteria) {
        var predicate = new StringBuilder(" from PartyEntity party where party.tenantId = :tenantId");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", tenant.value());
        if (criteria.type() != null) {
            predicate.append(" and party.type = :type");
            values.put("type", criteria.type());
        }
        if (criteria.recordStatus() != null) {
            predicate.append(" and party.recordStatus = :recordStatus");
            values.put("recordStatus", criteria.recordStatus());
        }
        Instant from = criteria.createdFrom();
        if (from != null) {
            predicate.append(" and party.createdAt >= :createdFrom");
            // Stored instants are microseconds: ceil the lower bound and floor the upper bound, never round them.
            Instant floor = from.truncatedTo(ChronoUnit.MICROS);
            values.put("createdFrom", floor.equals(from) ? floor : floor.plusNanos(1000));
        }
        Instant to = criteria.createdTo();
        if (to != null) {
            predicate.append(" and party.createdAt <= :createdTo");
            values.put("createdTo", to.truncatedTo(ChronoUnit.MICROS));
        }
        where = predicate.toString();
        parameters = Map.copyOf(values);
    }

    Uni<Long> count(Mutiny.Session session) {
        return query(session, "select count(party)", Long.class, null, "").getSingleResult();
    }

    Uni<List<PartySummaryResult>> summaries(Mutiny.Session session, @Nullable PartyPageBoundary boundary, int limit) {
        boolean previous = boundary != null && boundary.direction() == PartyPageBoundary.Direction.PREVIOUS;
        String order = previous ? " order by party.createdAt asc, party.id asc"
                : " order by party.createdAt desc, party.id desc";
        return query(session, SUMMARY, Object[].class, boundary, order).setMaxResults(limit).getResultList()
                .map(rows -> {
                    List<PartySummaryResult> summaries = rows.stream().map(PartyPageSql::summary).toList();
                    return previous ? summaries.reversed() : summaries;
                });
    }

    Uni<Boolean> exists(Mutiny.Session session, PartyPageBoundary boundary) {
        return query(session, "select party.id", UUID.class, boundary, "").setMaxResults(1)
                .getSingleResultOrNull().map(Objects::nonNull);
    }

    private <T> Mutiny.SelectionQuery<T> query(Mutiny.Session session, String projection, Class<T> resultType,
            @Nullable PartyPageBoundary boundary, String order) {
        String predicate = boundary == null ? "" : boundaryPredicate(boundary.direction());
        var query = session.createSelectionQuery(projection + where + predicate + order, resultType);
        parameters.forEach(query::setParameter);
        if (boundary != null) {
            query.setParameter("boundaryCreatedAt", boundary.position().createdAt());
            query.setParameter("boundaryPartyId", boundary.position().partyId().value());
        }
        return query;
    }

    private static String boundaryPredicate(PartyPageBoundary.Direction direction) {
        return direction == PartyPageBoundary.Direction.NEXT
                ? " and (party.createdAt < :boundaryCreatedAt or (party.createdAt = :boundaryCreatedAt and party.id < :boundaryPartyId))"
                : " and (party.createdAt > :boundaryCreatedAt or (party.createdAt = :boundaryCreatedAt and party.id > :boundaryPartyId))";
    }

    private static PartySummaryResult summary(Object[] row) {
        return new PartySummaryResult(new PartyId((UUID) row[0]), (PartyType) row[1], (String) row[2],
                (PartyRecordStatus) row[3], (Instant) row[4], new PartyVersion((Long) row[5]));
    }
}
