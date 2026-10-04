package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** Reads one owner-qualified safe identifier projection inside a bounded read-only reactive transaction. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierDetailReader {

    static final String FIND_BY_ID = """
            select identifier.id, identifier.partyId, identifier.identifierSchemeId,
                   scheme.code, identifier.maskedValue, identifier.status, identifier.primary,
                   identifier.issuerCode, identifier.issuedOn, identifier.expiresOn,
                   identifier.verifiedAt, identifier.verifiedBy, identifier.version,
                   identifier.createdAt, identifier.updatedAt
            from PartyIdentifierEntity identifier
            join IdentifierSchemeEntity scheme on scheme.id = identifier.identifierSchemeId
            where identifier.tenantId = :tenantId
              and identifier.partyId = :partyId
              and identifier.id = :identifierId
            """;

    private final Mutiny.SessionFactory sessions;
    private final PartyQueryTimeouts timeouts;

    @Inject
    public PartyIdentifierDetailReader(Mutiny.SessionFactory sessions, PartyQueryTimeouts timeouts) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
    }

    /** Returns absence for every missing or wrong-owner target without loading protected entity state. */
    public Uni<Optional<PartyIdentifierResult>> findById(RequestMetadata metadata, PartyId partyId,
            PartyIdentifierId identifierId) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(identifierId, "identifierId");
        return sessions.withTransaction((session, transaction) -> {
            session.setDefaultReadOnly(true);
            return timeouts.limit(session.createNativeQuery("SET TRANSACTION READ ONLY").executeUpdate()
                    .call(() -> timeouts.configure(session))
                    .flatMap(ignored -> session.createSelectionQuery(FIND_BY_ID, Object[].class)
                            .setParameter("tenantId", metadata.tenantId().value())
                            .setParameter("partyId", partyId.value())
                            .setParameter("identifierId", identifierId.value())
                            .getSingleResultOrNull().map(PartyIdentifierDetailReader::safeResult)));
        }).onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    private static Optional<PartyIdentifierResult> safeResult(@Nullable Object[] row) {
        return Optional.ofNullable(row).map(HibernateReactivePartyIdentifierReadAdapter::toResult);
    }
}
