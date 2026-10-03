package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Builds bounded parameterized global keyset and neighbor queries with identical exact filters. */
final class IdentifierSchemePageSql {

    private final String where;
    private final Map<String, Object> parameters;
    private final int limit;

    IdentifierSchemePageSql(IdentifierSchemeSearchCriteria criteria) {
        var predicate = new StringBuilder("from IdentifierSchemeEntity scheme where 1 = 1");
        Map<String, Object> values = new LinkedHashMap<>();
        if (criteria.issuingCountryCode() != null) {
            predicate.append(" and scheme.issuingCountryCode = :country");
            values.put("country", criteria.issuingCountryCode());
        }
        if (criteria.category() != null) {
            predicate.append(" and scheme.category = :category");
            values.put("category", criteria.category());
        }
        if (criteria.applicableSubjectType() != null) {
            predicate.append(" and scheme.applicableSubjectType = :subject");
            values.put("subject", criteria.applicableSubjectType());
        }
        if (criteria.status() != null) {
            predicate.append(" and scheme.status = :status");
            values.put("status", criteria.status());
        }
        where = predicate.toString();
        parameters = Map.copyOf(values);
        limit = criteria.limit();
    }

    /** Includes one lookahead row in traversal order; the caller trims before reversing backward pages. */
    Uni<List<IdentifierSchemeEntity>> page(Mutiny.Session session, @Nullable IdentifierSchemePageBoundary boundary) {
        boolean backward = boundary != null && boundary.direction() == IdentifierSchemePageBoundary.Direction.PREVIOUS;
        String order = backward ? " order by scheme.createdAt desc, scheme.id desc"
                : " order by scheme.createdAt asc, scheme.id asc";
        return query(session, "select scheme", IdentifierSchemeEntity.class, boundary, order)
                .setMaxResults(limit + 1).getResultList();
    }

    Uni<Boolean> exists(Mutiny.Session session, IdentifierSchemePageBoundary boundary) {
        return query(session, "select scheme.id", UUID.class, boundary, "")
                .setMaxResults(1).getSingleResultOrNull().map(Objects::nonNull);
    }

    private <T> Mutiny.SelectionQuery<T> query(Mutiny.Session session, String projection, Class<T> type,
            @Nullable IdentifierSchemePageBoundary boundary, String order) {
        String position = boundary == null ? "" : boundaryPredicate(boundary.direction());
        var query = session.createSelectionQuery(projection + " " + where + position + order, type);
        parameters.forEach(query::setParameter);
        if (boundary != null) {
            query.setParameter("createdAt", boundary.position().createdAt());
            query.setParameter("id", boundary.position().schemeId().value());
        }
        return query;
    }

    private static String boundaryPredicate(IdentifierSchemePageBoundary.Direction direction) {
        return direction == IdentifierSchemePageBoundary.Direction.NEXT
                ? " and (scheme.createdAt, scheme.id) > (:createdAt, :id)"
                : " and (scheme.createdAt, scheme.id) < (:createdAt, :id)";
    }
}
