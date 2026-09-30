package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps official identifier-scheme definitions and their processing rules.
 */
@Entity
@Table(name = "identifier_schemes")
public class IdentifierSchemeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "issuing_country_code", nullable = false, columnDefinition = "char(2)")
    private String issuingCountryCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false, columnDefinition = "identifier_category")
    private IdentifierCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "applicable_subject_type", nullable = false, columnDefinition = "identifier_subject_type")
    private IdentifierSubjectType applicableSubjectType;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "normalizer_key", nullable = false, length = 64)
    private String normalizerKey;

    @Column(name = "validator_key", nullable = false, length = 64)
    private String validatorKey;

    @Column(name = "minimum_length")
    private Short minimumLength;

    @Column(name = "maximum_length")
    private Short maximumLength;

    @Column(name = "requires_expiration", nullable = false)
    private boolean requiresExpiration;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "identifier_scheme_status")
    private IdentifierSchemeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IdentifierSchemeEntity() {
    }

    static Builder builder() {
        return new Builder();
    }

    private IdentifierSchemeEntity(Builder builder) {
        this.id = builder.id;
        this.code = builder.code;
        this.issuingCountryCode = builder.issuingCountryCode;
        this.category = builder.category;
        this.applicableSubjectType = builder.applicableSubjectType;
        this.name = builder.name;
        this.description = builder.description;
        this.normalizerKey = builder.normalizerKey;
        this.validatorKey = builder.validatorKey;
        this.minimumLength = builder.minimumLength;
        this.maximumLength = builder.maximumLength;
        this.requiresExpiration = builder.requiresExpiration;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.createdBy = builder.createdBy;
        this.updatedAt = builder.updatedAt;
        this.updatedBy = builder.updatedBy;
        this.version = builder.version;
    }

    /**
     * Builds instances of {@link IdentifierSchemeEntity}.
     */
    static final class Builder {
        private UUID id;
        private String code;
        private String issuingCountryCode;
        private IdentifierCategory category;
        private IdentifierSubjectType applicableSubjectType;
        private String name;
        private String description;
        private String normalizerKey;
        private String validatorKey;
        private Short minimumLength;
        private Short maximumLength;
        private boolean requiresExpiration;
        private IdentifierSchemeStatus status;
        private Instant createdAt;
        private String createdBy;
        private Instant updatedAt;
        private String updatedBy;
        private long version;

        private Builder() {
        }

        Builder id(UUID id) {
            this.id = id;
            return this;
        }

        Builder code(String code) {
            this.code = code;
            return this;
        }

        Builder issuingCountryCode(String issuingCountryCode) {
            this.issuingCountryCode = issuingCountryCode;
            return this;
        }

        Builder category(IdentifierCategory category) {
            this.category = category;
            return this;
        }

        Builder applicableSubjectType(IdentifierSubjectType applicableSubjectType) {
            this.applicableSubjectType = applicableSubjectType;
            return this;
        }

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder description(String description) {
            this.description = description;
            return this;
        }

        Builder normalizerKey(String normalizerKey) {
            this.normalizerKey = normalizerKey;
            return this;
        }

        Builder validatorKey(String validatorKey) {
            this.validatorKey = validatorKey;
            return this;
        }

        Builder minimumLength(Short minimumLength) {
            this.minimumLength = minimumLength;
            return this;
        }

        Builder maximumLength(Short maximumLength) {
            this.maximumLength = maximumLength;
            return this;
        }

        Builder requiresExpiration(boolean requiresExpiration) {
            this.requiresExpiration = requiresExpiration;
            return this;
        }

        Builder status(IdentifierSchemeStatus status) {
            this.status = status;
            return this;
        }

        Builder auditInfo(AuditInfo auditInfo) {
            if (auditInfo != null) {
                this.createdAt = auditInfo.createdAt();
                this.createdBy = auditInfo.createdBy();
                this.updatedAt = auditInfo.updatedAt();
                this.updatedBy = auditInfo.updatedBy();
            }
            return this;
        }

        Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        Builder version(long version) {
            this.version = version;
            return this;
        }

        IdentifierSchemeEntity build() {
            return new IdentifierSchemeEntity(this);
        }
    }

    UUID id() {
        return id;
    }

    String code() {
        return code;
    }

    String issuingCountryCode() {
        return issuingCountryCode;
    }

    IdentifierCategory category() {
        return category;
    }

    IdentifierSubjectType applicableSubjectType() {
        return applicableSubjectType;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    String normalizerKey() {
        return normalizerKey;
    }

    String validatorKey() {
        return validatorKey;
    }

    Short minimumLength() {
        return minimumLength;
    }

    Short maximumLength() {
        return maximumLength;
    }

    boolean requiresExpiration() {
        return requiresExpiration;
    }

    IdentifierSchemeStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    String createdBy() {
        return createdBy;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    String updatedBy() {
        return updatedBy;
    }

    long version() {
        return version;
    }
}
