package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.ExactIdentifierSearchSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchFilters;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchHit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.infrastructure.security.IdentifierProtectionIdentity;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Executes bound joined scalar projections without loading managed Party/identifier aggregates or decrypting exact matches. */
final class IdentifierSearchSql {
    static final int BATCH_SIZE = 256;
    private static final String QUALIFIED = """
            from PartyIdentifierEntity i
            join PartyEntity p on p.id = i.partyId and p.tenantId = i.tenantId
            join IdentifierSchemeEntity s on s.id = i.identifierSchemeId
            where i.tenantId = :tenant and p.tenantId = :tenant
              and i.identifierSchemeId = :scheme
              and i.status in (:identifierStates) and p.recordStatus in (:partyStates)
            """;
    private static final String SAFE_FIELDS = "p.id, p.type, p.displayName, p.recordStatus, s.code, i.maskedValue, i.status, i.primary, i.createdAt, i.id";
    private static final String ORDER = " order by i.createdAt desc, i.id desc";
    private static final String KEYSET = " and (i.createdAt < :created or (i.createdAt = :created and i.id < :id))";
    private final JcaIdentifierProtectionAdapter protection;

    IdentifierSearchSql(JcaIdentifierProtectionAdapter protection) {
        this.protection = protection;
    }

    Uni<ExactIdentifierSearchSlice> exact(Mutiny.Session session, TenantId tenant, IdentifierSchemeId scheme,
            IdentifierSearchSelector selector, IdentifierSearchFilters filters) {
        if (filters.matchesNothing()) return Uni.createFrom().item(new ExactIdentifierSearchSlice(List.of(), 0));
        var hash = protection.lookupHash(tenant, selector.normalizedValue());
        if (hash.isEmpty()) return Uni.createFrom().item(new ExactIdentifierSearchSlice(List.of(), 0));
        String predicate = " and i.normalizedValueHash = :hash";
        return bind(session.createSelectionQuery("select count(i.id) " + QUALIFIED + predicate, Long.class), tenant, scheme, filters)
                .setParameter("hash", hash.get()).getSingleResult().flatMap(count -> {
                    Math.toIntExact(count);
                    if (count == 0) return Uni.createFrom().item(new ExactIdentifierSearchSlice(List.of(), 0));
                    return bind(session.createSelectionQuery("select " + SAFE_FIELDS + " " + QUALIFIED + predicate + ORDER, Object[].class),
                            tenant, scheme, filters).setParameter("hash", hash.get()).getResultList()
                            .map(rows -> new ExactIdentifierSearchSlice(rows.stream().map(IdentifierSearchSql::hit).toList(), count));
                });
    }

    Uni<List<Candidate>> candidates(Mutiny.Session session, TenantId tenant, IdentifierSchemeId scheme,
            IdentifierSearchFilters filters, Optional<PartyIdentifierPagePosition> after) {
        if (filters.matchesNothing()) return Uni.createFrom().item(List.of());
        String fields = SAFE_FIELDS + ", i.encryptedValue, i.tenantId, i.identifierSchemeId, i.encryptionKeyVersion, i.normalizationVersion";
        var query = bind(session.createSelectionQuery("select " + fields + " " + QUALIFIED + (after.isPresent() ? KEYSET : "") + ORDER,
                Object[].class), tenant, scheme, filters).setMaxResults(BATCH_SIZE);
        after.ifPresent(position -> query.setParameter("created", position.createdAt()).setParameter("id", position.identifierId().value()));
        return query.getResultList().map(rows -> rows.stream().map(IdentifierSearchSql::candidate).toList());
    }

    private static <T> Mutiny.SelectionQuery<T> bind(Mutiny.SelectionQuery<T> query, TenantId tenant,
            IdentifierSchemeId scheme, IdentifierSearchFilters filters) {
        return query.setParameter("tenant", tenant.value()).setParameter("scheme", scheme.value())
                .setParameter("identifierStates", filters.identifierStatuses()).setParameter("partyStates", filters.partyRecordStatuses());
    }

    private static IdentifierSearchHit hit(Object[] row) {
        if (row.length != 10 && row.length != 15) throw new IllegalStateException("Identifier search projection is invalid");
        return new IdentifierSearchHit(new PartyId(field(row, 0, UUID.class)), field(row, 1, PartyType.class),
                field(row, 2, String.class), field(row, 3, PartyRecordStatus.class), field(row, 4, String.class),
                field(row, 5, String.class), field(row, 6, PartyIdentifierStatus.class), field(row, 7, Boolean.class),
                new PartyIdentifierPagePosition(field(row, 8, Instant.class), new PartyIdentifierId(field(row, 9, UUID.class))));
    }

    private static Candidate candidate(Object[] row) {
        if (row.length != 15) throw new IllegalStateException("Identifier search candidate is invalid");
        var safe = hit(row);
        return new Candidate(safe, field(row, 10, String.class), new IdentifierProtectionIdentity(new TenantId(field(row, 11, UUID.class)),
                safe.partyId(), safe.position().identifierId(), new IdentifierSchemeId(field(row, 12, UUID.class)),
                new IdentifierRuleVersion(field(row, 14, Short.class)), field(row, 13, Short.class)));
    }

    private static <T> T field(Object[] row, int index, Class<T> type) {
        if (!type.isInstance(row[index])) throw new IllegalStateException("Identifier search projection field is invalid");
        return type.cast(row[index]);
    }

    /** Keeps detached private ciphertext/AAD material inside Infrastructure and out of diagnostics. */
    record Candidate(IdentifierSearchHit hit, String encryptedValue, IdentifierProtectionIdentity identity) {
        @Override public String toString() { return "IdentifierSearchCandidate[redacted]"; }
    }
}
