package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** Detaches catalog configuration and original audit attribution from persistence-managed state. */
public record IdentifierSchemeResult(
        IdentifierSchemeId id, String code, String issuingCountryCode, IdentifierCategory category,
        IdentifierSubjectType applicableSubjectType, String name, @Nullable String description,
        String normalizerKey, String validatorKey, @Nullable Integer minimumLength, @Nullable Integer maximumLength,
        boolean requiresExpiration, IdentifierSchemeStatus status, IdentifierSchemeVersion version,
        Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {

    public IdentifierSchemeResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(issuingCountryCode, "issuingCountryCode");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(applicableSubjectType, "applicableSubjectType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(normalizerKey, "normalizerKey");
        Objects.requireNonNull(validatorKey, "validatorKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }

    public static IdentifierSchemeResult fromAggregate(IdentifierScheme scheme) {
        Objects.requireNonNull(scheme, "scheme");
        var audit = scheme.auditInfo();
        return new IdentifierSchemeResult(scheme.id(), scheme.code(), scheme.issuingCountryCode(), scheme.category(),
                scheme.applicableSubjectType(), scheme.name(), scheme.description(), scheme.normalizerKey(),
                scheme.validatorKey(), scheme.minimumLength(), scheme.maximumLength(), scheme.requiresExpiration(),
                scheme.status(), scheme.version(), audit.createdAt(), audit.createdBy(), audit.updatedAt(), audit.updatedBy());
    }

    /** Restores historical data using Domain invariants without reevaluating current rule availability. */
    public IdentifierScheme toAggregate() {
        return new IdentifierScheme(id, code, issuingCountryCode, category, applicableSubjectType, name, description,
                normalizerKey, validatorKey, minimumLength, maximumLength, requiresExpiration, status, version,
                new AuditInfo(createdAt, createdBy, updatedAt, updatedBy));
    }
}
