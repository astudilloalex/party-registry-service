package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.LockMode;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.Optional;

/** Performs global locking and exact draft/CAS writes using only the supplied owning reactive session. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class IdentifierSchemeMutationPersistence {

    private static final String UPDATE_SCHEME_QUERY = """
            update IdentifierSchemeEntity scheme set scheme.name = :name, scheme.description = :description,
                scheme.normalizerKey = :normalizer, scheme.validatorKey = :validator,
                scheme.minimumLength = :minimum, scheme.maximumLength = :maximum,
                scheme.requiresExpiration = :expiration, scheme.status = :status,
                scheme.version = :nextVersion, scheme.updatedAt = :updatedAt, scheme.updatedBy = :updatedBy
            where scheme.id = :id and scheme.version = :expectedVersion
            """;

    private final IdentifierSchemePersistenceMapper mapper;

    /** Reuses checked scheme mappings without modifying managed next-version entities. */
    @Inject
    public IdentifierSchemeMutationPersistence(IdentifierSchemePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    Uni<Boolean> codeExists(Mutiny.Session session, String code) {
        return session.createSelectionQuery("select scheme.id from IdentifierSchemeEntity scheme where scheme.code = :code", java.util.UUID.class)
                .setParameter("code", code).setMaxResults(1).getSingleResultOrNull().map(Objects::nonNull);
    }

    Uni<Optional<IdentifierScheme>> findForUpdate(Mutiny.Session session, IdentifierSchemeId id) {
        return session.createQuery("from IdentifierSchemeEntity scheme where scheme.id = :id", IdentifierSchemeEntity.class)
                .setParameter("id", id.value()).setLockMode(LockMode.PESSIMISTIC_WRITE).getSingleResultOrNull()
                .map(entity -> Optional.ofNullable(entity).map(mapper::toDomain));
    }

    Uni<IdentifierScheme> insert(Mutiny.Session session, IdentifierScheme candidate) {
        return Uni.createFrom().deferred(() -> {
            if (candidate.status() != IdentifierSchemeStatus.DRAFT || candidate.version().value() != 0
                    || !candidate.auditInfo().createdAt().equals(candidate.auditInfo().updatedAt())
                    || !candidate.auditInfo().createdBy().equals(candidate.auditInfo().updatedBy())
                    || candidate.auditInfo().createdAt().getNano() % 1_000 != 0) {
                throw new IllegalArgumentException("Inserted scheme must be an initial microsecond-attributed draft");
            }
            return session.persist(mapper.toEntity(candidate)).call(session::flush).invoke(session::clear)
                    .chain(() -> reload(session, candidate.id())).invoke(actual -> requireStoredCandidate(candidate, actual));
        });
    }

    Uni<IdentifierScheme> persistScheme(Mutiny.Session session, IdentifierScheme locked, IdentifierScheme candidate,
            IdentifierSchemeVersion expected) {
        return Uni.createFrom().deferred(() -> {
            validateCandidate(locked, candidate, expected);
            // Bulk HQL deliberately avoids Hibernate's managed-entity @Version increment.
            var mapped = mapper.toEntity(candidate);
            return session.createMutationQuery(UPDATE_SCHEME_QUERY)
                    .setParameter("name", mapped.name()).setParameter("description", mapped.description())
                    .setParameter("normalizer", mapped.normalizerKey()).setParameter("validator", mapped.validatorKey())
                    .setParameter("minimum", mapped.minimumLength()).setParameter("maximum", mapped.maximumLength())
                    .setParameter("expiration", mapped.requiresExpiration()).setParameter("status", mapped.status())
                    .setParameter("nextVersion", mapped.version()).setParameter("updatedAt", mapped.updatedAt())
                    .setParameter("updatedBy", mapped.updatedBy()).setParameter("id", mapped.id())
                    .setParameter("expectedVersion", expected.value()).executeUpdate().invoke(session::clear)
                    .chain(count -> count == 1 ? reload(session, candidate.id())
                            : Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdentifierSchemeVersionMismatch())))
                    .invoke(actual -> requireStoredCandidate(candidate, actual));
        });
    }

    private Uni<IdentifierScheme> reload(Mutiny.Session session, IdentifierSchemeId id) {
        return session.find(IdentifierSchemeEntity.class, id.value()).map(entity ->
                mapper.toDomain(Objects.requireNonNull(entity, "Written scheme cannot be reloaded")));
    }

    private static void validateCandidate(IdentifierScheme source, IdentifierScheme candidate, IdentifierSchemeVersion expected) {
        if (!source.id().equals(candidate.id()) || !source.code().equals(candidate.code())
                || !source.issuingCountryCode().equals(candidate.issuingCountryCode()) || source.category() != candidate.category()
                || source.applicableSubjectType() != candidate.applicableSubjectType()
                || !source.auditInfo().createdAt().equals(candidate.auditInfo().createdAt())
                || !source.auditInfo().createdBy().equals(candidate.auditInfo().createdBy())
                || expected.value() == Long.MAX_VALUE || candidate.version().value() != expected.value() + 1
                || !candidate.auditInfo().updatedAt().isAfter(source.auditInfo().updatedAt())
                || candidate.auditInfo().updatedAt().getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("Scheme candidate does not retain its locked identity, creation audit, and exact next version");
        }
    }

    private static void requireStoredCandidate(IdentifierScheme candidate, IdentifierScheme actual) {
        if (!IdentifierSchemeResult.fromAggregate(candidate).equals(IdentifierSchemeResult.fromAggregate(actual))) {
            throw new IllegalStateException("Stored scheme does not match the accepted candidate");
        }
    }
}
