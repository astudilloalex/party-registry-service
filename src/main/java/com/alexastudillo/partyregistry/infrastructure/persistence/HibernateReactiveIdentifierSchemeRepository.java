package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Optional;

/**
 * Resolves complete identifier-scheme definitions through Hibernate Reactive.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveIdentifierSchemeRepository implements IdentifierSchemeRepository {

    private final Mutiny.SessionFactory sessionFactory;
    private final IdentifierSchemePersistenceMapper mapper;

    @Inject
    public HibernateReactiveIdentifierSchemeRepository(
            Mutiny.SessionFactory sessionFactory,
            IdentifierSchemePersistenceMapper mapper) {
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<IdentifierScheme>> findByCode(String code) {
        Uni<Optional<IdentifierScheme>> operation = sessionFactory.withSession(session -> session
                .createQuery("""
                        from IdentifierSchemeEntity scheme
                        where scheme.code = :code
                        """, IdentifierSchemeEntity.class)
                .setParameter("code", code)
                .getSingleResultOrNull()
                .map(entity -> Optional.ofNullable(entity).map(mapper::toDomain)));
        return operation
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
