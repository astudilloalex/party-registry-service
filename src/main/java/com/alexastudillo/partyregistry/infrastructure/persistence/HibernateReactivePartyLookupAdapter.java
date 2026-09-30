package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Optional;

/**
 * Resolves tenant-owned Party types without exposing Party details or
 * foreign-tenant rows.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyLookupAdapter implements PartyLookupPort {

    private static final String FIND_PARTY_TYPE = """
            select party.type
            from PartyEntity party
            where party.tenantId = :tenantId
              and party.id = :partyId
            """;
    private static final String PARAM_TENANT_ID = "tenantId";
    private static final String PARAM_PARTY_ID = "partyId";

    private final Mutiny.SessionFactory sessionFactory;

    @Inject
    public HibernateReactivePartyLookupAdapter(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Uni<Optional<PartyType>> findType(TenantId tenantId, PartyId partyId) {
        Uni<Optional<PartyType>> operation = sessionFactory.withSession(session -> session
                .createQuery(FIND_PARTY_TYPE, PartyType.class)
                .setParameter(PARAM_TENANT_ID, tenantId.value())
                .setParameter(PARAM_PARTY_ID, partyId.value())
                .getSingleResultOrNull()
                .map(Optional::ofNullable));
        return operation
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
