package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Maps identifier schemes to and from their Hibernate persistence model.
 */
@ApplicationScoped
public class IdentifierSchemePersistenceMapper {

    IdentifierSchemeEntity toEntity(IdentifierScheme scheme) {
        Objects.requireNonNull(scheme, "scheme");
        return new IdentifierSchemeEntity(
                scheme.id().value(),
                scheme.code(),
                scheme.issuingCountryCode(),
                scheme.category(),
                scheme.applicableSubjectType(),
                scheme.name(),
                scheme.description(),
                scheme.normalizerKey(),
                scheme.validatorKey(),
                toSmallint(scheme.minimumLength(), "minimumLength"),
                toSmallint(scheme.maximumLength(), "maximumLength"),
                scheme.requiresExpiration(),
                scheme.status(),
                scheme.auditInfo(),
                scheme.version().value());
    }

    IdentifierScheme toDomain(IdentifierSchemeEntity entity) {
        Objects.requireNonNull(entity, "entity");
        return new IdentifierScheme(
                new IdentifierSchemeId(entity.id()),
                entity.code(),
                entity.issuingCountryCode(),
                entity.category(),
                entity.applicableSubjectType(),
                entity.name(),
                entity.description(),
                entity.normalizerKey(),
                entity.validatorKey(),
                toInteger(entity.minimumLength()),
                toInteger(entity.maximumLength()),
                entity.requiresExpiration(),
                entity.status(),
                new IdentifierSchemeVersion(entity.version()),
                new AuditInfo(
                        entity.createdAt(),
                        entity.createdBy(),
                        entity.updatedAt(),
                        entity.updatedBy()));
    }

    private static Short toSmallint(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " does not fit the database smallint column");
        }
        return value.shortValue();
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
