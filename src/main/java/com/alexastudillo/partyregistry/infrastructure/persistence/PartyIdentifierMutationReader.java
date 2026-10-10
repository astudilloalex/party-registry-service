package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.LockMode;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Optional;
import java.util.UUID;

/** Restores tenant/owner-qualified mutation inputs under caller-owned shared root and serialized child row locks. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierMutationReader {

    private static final String SCOPE = "tenant_id = :tenant AND party_id = :party AND identifier_scheme_id = :scheme";
    private static final String TENANT_PARAMETER = "tenant";
    private static final String PARTY_PARAMETER = "party";
    private final PartyIdentifierPersistenceMapper identifiers;
    private final IdentifierSchemePersistenceMapper schemes;

    @Inject
    public PartyIdentifierMutationReader(PartyIdentifierPersistenceMapper identifiers, IdentifierSchemePersistenceMapper schemes) {
        this.identifiers = identifiers;
        this.schemes = schemes;
    }

    Uni<Optional<PartyType>> findOwnedParty(Mutiny.Session session, UUID tenant, UUID party) {
        return session.createNativeQuery("SELECT cast(type AS text) FROM parties WHERE tenant_id = :tenant AND id = :party FOR SHARE", String.class)
                .setParameter(TENANT_PARAMETER, tenant).setParameter(PARTY_PARAMETER, party).getSingleResultOrNull()
                .map(value -> Optional.ofNullable(value).map(PartyType::valueOf));
    }

    Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(Mutiny.Session session, UUID tenant, UUID party, UUID identifier) {
        return session.createNativeQuery("SELECT identifier_scheme_id FROM party_identifiers WHERE tenant_id = :tenant AND party_id = :party AND id = :identifier", UUID.class)
                .setParameter(TENANT_PARAMETER, tenant).setParameter(PARTY_PARAMETER, party).setParameter("identifier", identifier).getSingleResultOrNull()
                .map(value -> Optional.ofNullable(value).map(IdentifierSchemeId::new));
    }

    Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(Mutiny.Session session, String code) {
        return session.createSelectionQuery("SELECT scheme.id FROM IdentifierSchemeEntity scheme WHERE scheme.code = :code", UUID.class)
                .setParameter("code", code).getSingleResultOrNull().map(value -> Optional.ofNullable(value).map(IdentifierSchemeId::new));
    }

    Uni<Optional<IdentifierScheme>> findSchemeForRegistration(Mutiny.Session session, UUID schemeId) {
        return session.createSelectionQuery("FROM IdentifierSchemeEntity scheme WHERE scheme.id = :scheme", IdentifierSchemeEntity.class)
                .setParameter("scheme", schemeId).setLockMode(LockMode.PESSIMISTIC_READ).getSingleResultOrNull()
                .map(value -> Optional.ofNullable(value).map(schemes::toDomain));
    }

    Uni<Optional<PartyIdentifier>> findForUpdate(Mutiny.Session session, PartyIdentifierMutationLocks.Scope scope, UUID identifier) {
        session.clear();
        return scoped(session, scope, "SELECT * FROM party_identifiers WHERE " + SCOPE + " AND id = :identifier FOR UPDATE")
                .setParameter("identifier", identifier).getSingleResultOrNull().map(value -> Optional.ofNullable(value).map(identifiers::toDomain));
    }

    Uni<Optional<PartyIdentifier>> findCurrentPrimary(Mutiny.Session session, PartyIdentifierMutationLocks.Scope scope) {
        session.clear();
        return scoped(session, scope, "SELECT * FROM party_identifiers WHERE " + SCOPE + " AND status = 'VERIFIED' AND is_primary FOR UPDATE")
                .getSingleResultOrNull().map(value -> Optional.ofNullable(value).map(identifiers::toDomain));
    }

    private static Mutiny.SelectionQuery<PartyIdentifierEntity> scoped(Mutiny.Session session, PartyIdentifierMutationLocks.Scope scope, String sql) {
        return session.createNativeQuery(sql, PartyIdentifierEntity.class).setParameter(TENANT_PARAMETER, scope.tenantId())
                .setParameter(PARTY_PARAMETER, scope.partyId()).setParameter("scheme", scope.schemeId());
    }
}
