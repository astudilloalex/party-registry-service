package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Maps one independently persisted protected Party identifier.
 */
@Entity
@Table(name = "party_identifiers")
public class PartyIdentifierEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "identifier_scheme_id", nullable = false)
    private UUID identifierSchemeId;

    @Column(name = "issuer_code", length = 64)
    private String issuerCode;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "text")
    private String encryptedValue;

    @Column(name = "encryption_key_version", nullable = false)
    private short encryptionKeyVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "normalized_value_hash", nullable = false, columnDefinition = "char(64)")
    private String normalizedValueHash;

    @Column(name = "masked_value", nullable = false, length = 64)
    private String maskedValue;

    @Column(name = "normalization_version", nullable = false)
    private short normalizationVersion;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "party_identifier_status")
    private PartyIdentifierStatus status;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by", length = 128)
    private String verifiedBy;

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

    protected PartyIdentifierEntity() {
    }

    static Builder builder() {
        return new Builder();
    }

    private PartyIdentifierEntity(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.partyId = builder.partyId;
        this.identifierSchemeId = builder.identifierSchemeId;
        this.issuerCode = builder.issuerCode;
        this.encryptedValue = builder.encryptedValue;
        this.encryptionKeyVersion = builder.encryptionKeyVersion;
        this.normalizedValueHash = builder.normalizedValueHash;
        this.maskedValue = builder.maskedValue;
        this.normalizationVersion = builder.normalizationVersion;
        this.primary = builder.primary;
        this.status = builder.status;
        this.issuedOn = builder.issuedOn;
        this.expiresOn = builder.expiresOn;
        this.verifiedAt = builder.verifiedAt;
        this.verifiedBy = builder.verifiedBy;
        this.createdAt = builder.createdAt;
        this.createdBy = builder.createdBy;
        this.updatedAt = builder.updatedAt;
        this.updatedBy = builder.updatedBy;
        this.version = builder.version;
    }

    /**
     * Builds instances of {@link PartyIdentifierEntity}.
     */
    static final class Builder {
        private UUID id;
        private UUID tenantId;
        private UUID partyId;
        private UUID identifierSchemeId;
        private String issuerCode;
        private String encryptedValue;
        private short encryptionKeyVersion;
        private String normalizedValueHash;
        private String maskedValue;
        private short normalizationVersion;
        private boolean primary;
        private PartyIdentifierStatus status;
        private LocalDate issuedOn;
        private LocalDate expiresOn;
        private Instant verifiedAt;
        private String verifiedBy;
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

        Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        Builder partyId(UUID partyId) {
            this.partyId = partyId;
            return this;
        }

        Builder identifierSchemeId(UUID identifierSchemeId) {
            this.identifierSchemeId = identifierSchemeId;
            return this;
        }

        Builder issuerCode(String issuerCode) {
            this.issuerCode = issuerCode;
            return this;
        }

        Builder protectedValue(ProtectedIdentifierValue protectedValue) {
            if (protectedValue != null) {
                this.encryptedValue = protectedValue.encryptedValue();
                this.encryptionKeyVersion = (short) protectedValue.encryptionKeyVersion();
                this.normalizedValueHash = protectedValue.normalizedValueHash();
                this.maskedValue = protectedValue.maskedValue();
                this.normalizationVersion = (short) protectedValue.normalizationVersion().value();
            }
            return this;
        }

        Builder encryptedValue(String encryptedValue) {
            this.encryptedValue = encryptedValue;
            return this;
        }

        Builder encryptionKeyVersion(short encryptionKeyVersion) {
            this.encryptionKeyVersion = encryptionKeyVersion;
            return this;
        }

        Builder normalizedValueHash(String normalizedValueHash) {
            this.normalizedValueHash = normalizedValueHash;
            return this;
        }

        Builder maskedValue(String maskedValue) {
            this.maskedValue = maskedValue;
            return this;
        }

        Builder normalizationVersion(short normalizationVersion) {
            this.normalizationVersion = normalizationVersion;
            return this;
        }

        Builder primary(boolean primary) {
            this.primary = primary;
            return this;
        }

        Builder status(PartyIdentifierStatus status) {
            this.status = status;
            return this;
        }

        Builder issuedOn(LocalDate issuedOn) {
            this.issuedOn = issuedOn;
            return this;
        }

        Builder expiresOn(LocalDate expiresOn) {
            this.expiresOn = expiresOn;
            return this;
        }

        Builder verifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
            return this;
        }

        Builder verifiedBy(String verifiedBy) {
            this.verifiedBy = verifiedBy;
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

        PartyIdentifierEntity build() {
            return new PartyIdentifierEntity(this);
        }
    }

    UUID id() {
        return id;
    }

    UUID tenantId() {
        return tenantId;
    }

    UUID partyId() {
        return partyId;
    }

    UUID identifierSchemeId() {
        return identifierSchemeId;
    }

    String issuerCode() {
        return issuerCode;
    }

    String encryptedValue() {
        return encryptedValue;
    }

    short encryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    String normalizedValueHash() {
        return normalizedValueHash;
    }

    String maskedValue() {
        return maskedValue;
    }

    short normalizationVersion() {
        return normalizationVersion;
    }

    boolean isPrimary() {
        return primary;
    }

    PartyIdentifierStatus status() {
        return status;
    }

    LocalDate issuedOn() {
        return issuedOn;
    }

    LocalDate expiresOn() {
        return expiresOn;
    }

    Instant verifiedAt() {
        return verifiedAt;
    }

    String verifiedBy() {
        return verifiedBy;
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
