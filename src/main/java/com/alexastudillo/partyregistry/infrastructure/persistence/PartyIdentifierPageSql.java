package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Builds bound safe historical projections and strict tuple predicates within one caller-owned read session. */
final class PartyIdentifierPageSql {

    private static final String PROJECTION = """
            select identifier.id, identifier.partyId, identifier.identifierSchemeId,
                   scheme.code, identifier.maskedValue, identifier.status, identifier.primary,
                   identifier.issuerCode, identifier.issuedOn, identifier.expiresOn,
                   identifier.verifiedAt, identifier.verifiedBy, identifier.version,
                   identifier.createdAt, identifier.updatedAt
            """;
    private final String where;
    private final Map<String, Object> parameters;
    private final int limit;

    PartyIdentifierPageSql(PartyIdentifierSearchScope scope) {
        Objects.requireNonNull(scope, "scope");
        var criteria = scope.criteria();
        var predicate = new StringBuilder("""
                 from PartyIdentifierEntity identifier
                 join IdentifierSchemeEntity scheme on scheme.id = identifier.identifierSchemeId
                 where identifier.tenantId = :tenantId and identifier.partyId = :partyId
                """);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", scope.tenantId().value());
        values.put("partyId", scope.partyId().value());
        var schemeId = criteria.identifierSchemeId();
        if (schemeId != null) {
            predicate.append(" and identifier.identifierSchemeId = :schemeId");
            values.put("schemeId", schemeId.value());
        }
        String code = criteria.schemeCode();
        if (code != null) {
            predicate.append(" and scheme.code = :schemeCode");
            values.put("schemeCode", code);
        }
        var status = criteria.status();
        if (status != null) {
            predicate.append(" and identifier.status = :status");
            values.put("status", status);
        }
        Boolean primary = criteria.isPrimary();
        if (primary != null) {
            predicate.append(" and identifier.primary = :primary");
            values.put("primary", primary);
        }
        where = predicate.toString();
        parameters = Map.copyOf(values);
        limit = criteria.limit();
    }

    Uni<Long> count(Mutiny.Session session) {
        return query(session, "select count(identifier)", Long.class, null, "").getSingleResult();
    }

    Uni<List<PartyIdentifierResult>> page(Mutiny.Session session, @Nullable PartyIdentifierPageBoundary boundary) {
        boolean backward = boundary != null && boundary.direction() == PartyIdentifierPageBoundary.Direction.PREVIOUS;
        String order = backward ? " order by identifier.createdAt asc, identifier.id asc"
                : " order by identifier.createdAt desc, identifier.id desc";
        return query(session, PROJECTION, Object[].class, boundary, order).setMaxResults(limit).getResultList()
                .map(rows -> {
                    List<PartyIdentifierResult> items = rows.stream().map(HibernateReactivePartyIdentifierReadAdapter::toResult).toList();
                    return backward ? items.reversed() : items;
                });
    }

    Uni<Boolean> exists(Mutiny.Session session, PartyIdentifierPageBoundary boundary) {
        return query(session, "select identifier.id", UUID.class, boundary, "").setMaxResults(1)
                .getSingleResultOrNull().map(Objects::nonNull);
    }

    private <T> Mutiny.SelectionQuery<T> query(Mutiny.Session session, String projection, Class<T> resultType,
            @Nullable PartyIdentifierPageBoundary boundary, String order) {
        String continuation = boundary == null ? "" : boundaryPredicate(boundary.direction());
        var query = session.createSelectionQuery(projection + where + continuation + order, resultType);
        parameters.forEach(query::setParameter);
        if (boundary != null) {
            query.setParameter("boundaryCreatedAt", boundary.position().createdAt());
            query.setParameter("boundaryIdentifierId", boundary.position().identifierId().value());
        }
        return query;
    }

    private static String boundaryPredicate(PartyIdentifierPageBoundary.Direction direction) {
        return direction == PartyIdentifierPageBoundary.Direction.NEXT
                ? " and (identifier.createdAt < :boundaryCreatedAt or (identifier.createdAt = :boundaryCreatedAt and identifier.id < :boundaryIdentifierId))"
                : " and (identifier.createdAt > :boundaryCreatedAt or (identifier.createdAt = :boundaryCreatedAt and identifier.id > :boundaryIdentifierId))";
    }
}
