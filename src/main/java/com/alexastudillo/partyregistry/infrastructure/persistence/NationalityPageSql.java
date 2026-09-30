package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds bound nationality count, exact keyset page, and navigation queries for
 * one snapshot.
 */
final class NationalityPageSql {

    private final String where;
    private final Map<String, Object> parameters;

    NationalityPageSql(TenantId tenantId, PartyId partyId, NationalitySearchCriteria criteria) {
        var predicate = new StringBuilder("""
                from PartyNationalityEntity nationality
                join PartyEntity party on party.id = nationality.partyId
                where party.tenantId = :tenantId and party.id = :partyId
                  and (nationality.validFrom is null or nationality.validFrom <= :asOfDate)
                """);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", tenantId.value());
        values.put("partyId", partyId.value());
        values.put("asOfDate", criteria.asOfDate());
        if (!criteria.includeExpired()) {
            predicate.append(" and (nationality.validUntil is null or nationality.validUntil >= :asOfDate)");
        }
        if (criteria.countryCode() != null) {
            predicate.append(" and nationality.countryCode = :countryCode");
            values.put("countryCode", criteria.countryCode());
        }
        if (criteria.isPrimary() != null) {
            predicate.append(" and nationality.primary = :isPrimary");
            values.put("isPrimary", criteria.isPrimary());
        }
        where = predicate.toString();
        parameters = Map.copyOf(values);
    }

    Uni<Long> count(Mutiny.Session session) {
        return query(session, "select count(nationality)", Long.class, null, "").getSingleResult();
    }

    Uni<List<NationalityResult>> page(Mutiny.Session session, @Nullable NationalityPageBoundary boundary, int limit) {
        boolean previous = boundary != null && boundary.direction() == NationalityPageBoundary.Direction.PREVIOUS;
        String order = previous ? " order by nationality.createdAt asc, nationality.id asc"
                : " order by nationality.createdAt desc, nationality.id desc";
        return query(session, "select nationality", PartyNationalityEntity.class, boundary, order)
                .setMaxResults(limit).getResultList().map(rows -> {
                    List<NationalityResult> results = rows.stream().map(PartyNationalityEntity::toResult).toList();
                    return previous ? results.reversed() : results;
                });
    }

    Uni<Boolean> exists(Mutiny.Session session, NationalityPageBoundary boundary) {
        return query(session, "select nationality.id", UUID.class, boundary, "")
                .setMaxResults(1).getSingleResultOrNull().map(Objects::nonNull);
    }

    private <T> Mutiny.SelectionQuery<T> query(Mutiny.Session session, String projection,
            Class<T> type, @Nullable NationalityPageBoundary boundary, String order) {
        String position = boundary == null ? "" : boundaryPredicate(boundary.direction());
        var query = session.createSelectionQuery(projection + " " + where + position + order, type);
        parameters.forEach(query::setParameter);
        if (boundary != null) {
            query.setParameter("createdAt", boundary.position().createdAt());
            query.setParameter("nationalityId", boundary.position().nationalityId().value());
        }
        return query;
    }

    private static String boundaryPredicate(NationalityPageBoundary.Direction direction) {
        return direction == NationalityPageBoundary.Direction.NEXT
                ? " and (nationality.createdAt < :createdAt or (nationality.createdAt = :createdAt and nationality.id < :nationalityId))"
                : " and (nationality.createdAt > :createdAt or (nationality.createdAt = :createdAt and nationality.id > :nationalityId))";
    }
}
