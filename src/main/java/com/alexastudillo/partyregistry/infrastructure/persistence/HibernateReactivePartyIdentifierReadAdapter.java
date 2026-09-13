package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierReadPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads current masked identifiers in one scalar query without loading
 * protected material.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyIdentifierReadAdapter implements PartyIdentifierReadPort {

    static final String FIND_CURRENT_BY_PARTY = """
            select identifier.id, identifier.partyId, identifier.identifierSchemeId,
                   scheme.code, identifier.maskedValue, identifier.status, identifier.primary,
                   identifier.issuerCode, identifier.issuedOn, identifier.expiresOn,
                   identifier.verifiedAt, identifier.verifiedBy, identifier.version,
                   identifier.createdAt, identifier.updatedAt
            from PartyIdentifierEntity identifier
            join IdentifierSchemeEntity scheme on scheme.id = identifier.identifierSchemeId
            where identifier.tenantId = :tenantId
              and identifier.partyId = :partyId
              and identifier.status in (:pendingVerification, :verified)
              and (identifier.expiresOn is null or identifier.expiresOn >= :evaluatedOn)
            order by identifier.createdAt asc, identifier.id asc
            """;

    private final Mutiny.SessionFactory sessionFactory;

    @Inject
    public HibernateReactivePartyIdentifierReadAdapter(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Uni<List<PartyIdentifierResult>> findCurrentByParty(
            TenantId tenantId,
            PartyId partyId,
            LocalDate evaluatedOn) {
        Uni<List<PartyIdentifierResult>> operation = sessionFactory.withSession(session -> session
                .createQuery(FIND_CURRENT_BY_PARTY, Object[].class)
                .setParameter("tenantId", tenantId.value())
                .setParameter("partyId", partyId.value())
                .setParameter("pendingVerification", PartyIdentifierStatus.PENDING_VERIFICATION)
                .setParameter("verified", PartyIdentifierStatus.VERIFIED)
                .setParameter("evaluatedOn", evaluatedOn)
                .getResultList()
                .map(rows -> rows.stream()
                        .map(HibernateReactivePartyIdentifierReadAdapter::toResult)
                        .toList()));
        return operation
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    static PartyIdentifierResult toResult(Object[] row) {
        return new PartyIdentifierResult(
                new PartyIdentifierId((UUID) row[0]),
                new PartyId((UUID) row[1]),
                new IdentifierSchemeId((UUID) row[2]),
                (String) row[3],
                (String) row[4],
                (PartyIdentifierStatus) row[5],
                (Boolean) row[6],
                (String) row[7],
                (LocalDate) row[8],
                (LocalDate) row[9],
                (Instant) row[10],
                (String) row[11],
                new PartyIdentifierVersion((Long) row[12]),
                (Instant) row[13],
                (Instant) row[14]);
    }
}
