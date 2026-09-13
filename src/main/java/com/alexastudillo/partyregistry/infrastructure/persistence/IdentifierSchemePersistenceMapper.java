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
        return IdentifierSchemeEntity.builder()
                .id(scheme.id().value())
                .code(scheme.code())
                .issuingCountryCode(scheme.issuingCountryCode())
                .category(scheme.category())
                .applicableSubjectType(scheme.applicableSubjectType())
                .name(scheme.name())
                .description(scheme.description())
                .normalizerKey(scheme.normalizerKey())
                .validatorKey(scheme.validatorKey())
                .minimumLength(toSmallint(scheme.minimumLength(), "minimumLength"))
                .maximumLength(toSmallint(scheme.maximumLength(), "maximumLength"))
                .requiresExpiration(scheme.requiresExpiration())
                .status(scheme.status())
                .auditInfo(scheme.auditInfo())
                .version(scheme.version().value())
                .build();
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
