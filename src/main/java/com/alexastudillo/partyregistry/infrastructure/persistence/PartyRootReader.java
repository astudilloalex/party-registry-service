package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.Optional;

/**
 * Reads one qualified root/detail projection and detects corrupted subtype structure without fetching identifiers.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyRootReader {

    private static final String TENANT_ID = "tenantId";
    private static final String PARTY_ID = "partyId";
    private final Mutiny.SessionFactory sessionFactory;
    private final NaturalPersonPersistenceMapper naturalMapper;
    private final LegalEntityPersistenceMapper legalMapper;
    private final PartyQueryTimeouts timeouts;

    /** Creates the reader from reactive session ownership and the existing historical-state mappers. */
    @Inject
    public PartyRootReader(Mutiny.SessionFactory sessionFactory,
            NaturalPersonPersistenceMapper naturalMapper, LegalEntityPersistenceMapper legalMapper,
            PartyQueryTimeouts timeouts) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.naturalMapper = Objects.requireNonNull(naturalMapper, "naturalMapper");
        this.legalMapper = Objects.requireNonNull(legalMapper, "legalMapper");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
    }

    /**
     * Reads both Party subtypes in every lifecycle state from one qualified statement.
     *
     * @param tenantId trusted tenant, never inferred from returned data
     * @param partyId requested Party identity
     * @return safe matching details, or absence for missing/cross-tenant roots; corrupt rows fail internally
     */
    public Uni<Optional<PartyDetailsResult>> findDetails(TenantId tenantId, PartyId partyId) {
        return sessionFactory.withTransaction((session, transaction) -> {
            session.setDefaultReadOnly(true);
            return timeouts.limit(session.createNativeQuery("SET TRANSACTION READ ONLY").executeUpdate()
                    .call(() -> timeouts.configure(session)).flatMap(ignored -> read(session, tenantId, partyId))
                    .map(party -> party.map(PartyDetailsResult::fromAggregate)));
        })
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    /** Restores a detached aggregate within an existing read/mutation session without opening a second scope. */
    Uni<Optional<Party>> read(Mutiny.Session session, TenantId tenantId, PartyId partyId) {
        Objects.requireNonNull(tenantId, TENANT_ID);
        Objects.requireNonNull(partyId, PARTY_ID);
        return session.createQuery("""
                select party, naturalDetails, legalDetails
                from PartyEntity party
                left join party.naturalPersonDetails naturalDetails
                left join party.legalEntityDetails legalDetails
                where party.tenantId = :tenantId and party.id = :partyId
                """, Object[].class)
                .setParameter(TENANT_ID, tenantId.value())
                .setParameter(PARTY_ID, partyId.value())
                .getSingleResultOrNull()
                .map(row -> row == null ? Optional.empty() : Optional.of(toDomain(row)));
    }

    private Party toDomain(Object[] row) {
        PartyEntity root = (PartyEntity) row[0];
        NaturalPersonDetailsEntity natural = (NaturalPersonDetailsEntity) row[1];
        LegalEntityDetailsEntity legal = (LegalEntityDetailsEntity) row[2];
        if (natural != null && legal != null) {
            throw new IllegalStateException("Party has incompatible detail rows");
        }
        return switch (root.type()) {
            case NATURAL_PERSON -> naturalMapper.toDomain(root, natural);
            case LEGAL_ENTITY -> legalMapper.toDomain(root, legal);
        };
    }
}
