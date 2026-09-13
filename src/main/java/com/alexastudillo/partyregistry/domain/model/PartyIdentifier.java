package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents one independently versioned official identifier without plaintext values.
 */
public final class PartyIdentifier {

    private static final int MAX_ISSUER_CODE_LENGTH = 64;
    private static final int MAX_VERIFICATION_USER_LENGTH = 128;

    private final PartyIdentifierId identifierId;
    private final TenantId tenantId;
    private final PartyId partyId;
    private final IdentifierSchemeId identifierSchemeId;
    private final ProtectedIdentifierValue protectedValue;
    private final String issuerCode;
    private final LocalDate issuedOn;
    private final LocalDate expiresOn;
    private final boolean primary;
    private final PartyIdentifierStatus status;
    private final Instant verifiedAt;
    private final String verifiedBy;
    private final PartyIdentifierVersion version;
    private final AuditInfo auditInfo;

    private PartyIdentifier(Builder builder) {
        this.identifierId = require(builder.identifierId, DomainViolation.PARTY_IDENTIFIER_ID_REQUIRED,
                "PartyIdentifier ID is required");
        this.tenantId = require(builder.tenantId, DomainViolation.TENANT_ID_REQUIRED, "Tenant identifier is required");
        this.partyId = require(builder.partyId, DomainViolation.PARTY_ID_REQUIRED, "Party identifier is required");
        this.identifierSchemeId = require(builder.identifierSchemeId, DomainViolation.IDENTIFIER_SCHEME_ID_REQUIRED,
                "Identifier scheme ID is required");
        this.protectedValue = require(builder.protectedValue, DomainViolation.PROTECTED_IDENTIFIER_VALUE_REQUIRED,
                "Protected identifier value is required");
        validateRegistrationMetadata(builder.issuerCode, builder.issuedOn, builder.expiresOn);
        this.issuerCode = builder.issuerCode;
        this.issuedOn = builder.issuedOn;
        this.expiresOn = builder.expiresOn;
        this.primary = builder.primary;
        this.status = require(builder.status, DomainViolation.PARTY_IDENTIFIER_STATUS_REQUIRED,
                "PartyIdentifier status is required");
        validateVerification(builder.status, builder.verifiedAt, builder.verifiedBy);
        if (builder.status == PartyIdentifierStatus.EXPIRED && builder.expiresOn == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_EXPIRATION_DATE_REQUIRED,
                    "Expired identifier requires an expiration date");
        }
        this.verifiedAt = builder.verifiedAt;
        this.verifiedBy = builder.verifiedBy;
        this.version = require(builder.version, DomainViolation.PARTY_IDENTIFIER_VERSION_REQUIRED,
                "PartyIdentifier version is required");
        this.auditInfo = require(builder.auditInfo, DomainViolation.AUDIT_REQUIRED, "Audit information is required");
    }

    /**
     * Creates a builder for fluently constructing {@link PartyIdentifier} instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates registration metadata before sensitive identifier material is protected.
     *
     * @param issuerCode optional issuing authority code
     * @param issuedOn optional issue date
     * @param expiresOn optional expiration date
     * @throws DomainValidationException when the metadata cannot form a valid identifier
     */
    public static void validateRegistrationMetadata(
            String issuerCode,
            LocalDate issuedOn,
            LocalDate expiresOn) {
        validateIssuerCode(issuerCode);
        validateValidityDates(issuedOn, expiresOn);
    }

    public boolean isExpiredOn(LocalDate evaluatedOn) {
        if (evaluatedOn == null) {
            throw new DomainValidationException(
                    DomainViolation.EVALUATION_DATE_REQUIRED,
                    "Evaluation date is required");
        }
        return expiresOn != null && expiresOn.isBefore(evaluatedOn);
    }

    public PartyIdentifierId identifierId() {
        return identifierId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PartyId partyId() {
        return partyId;
    }

    public IdentifierSchemeId identifierSchemeId() {
        return identifierSchemeId;
    }

    public ProtectedIdentifierValue protectedValue() {
        return protectedValue;
    }

    public String issuerCode() {
        return issuerCode;
    }

    public LocalDate issuedOn() {
        return issuedOn;
    }

    public LocalDate expiresOn() {
        return expiresOn;
    }

    public boolean isPrimary() {
        return primary;
    }

    public PartyIdentifierStatus status() {
        return status;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public String verifiedBy() {
        return verifiedBy;
    }

    public PartyIdentifierVersion version() {
        return version;
    }

    public AuditInfo auditInfo() {
        return auditInfo;
    }

    private static void validateIssuerCode(String value) {
        if (value != null && value.codePointCount(0, value.length()) > MAX_ISSUER_CODE_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_ISSUER_CODE_TOO_LONG,
                    "Identifier issuer code exceeds the maximum length");
        }
    }

    private static void validateValidityDates(LocalDate issuedOn, LocalDate expiresOn) {
        if (issuedOn != null && expiresOn != null && expiresOn.isBefore(issuedOn)) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER,
                    "Identifier expiration date cannot precede its issue date");
        }
    }

    private static void validateVerification(
            PartyIdentifierStatus status,
            Instant verifiedAt,
            String verifiedBy) {
        if (status == PartyIdentifierStatus.VERIFIED && verifiedAt == null) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VERIFICATION_TIMESTAMP_REQUIRED,
                    "Verified identifier requires a verification timestamp");
        }
        if (status == PartyIdentifierStatus.VERIFIED && (verifiedBy == null || verifiedBy.isBlank())) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VERIFICATION_USER_REQUIRED,
                    "Verified identifier requires a verification user");
        }
        if (verifiedBy != null && verifiedBy.codePointCount(0, verifiedBy.length()) > MAX_VERIFICATION_USER_LENGTH) {
            throw new DomainValidationException(
                    DomainViolation.IDENTIFIER_VERIFICATION_USER_TOO_LONG,
                    "Identifier verification user exceeds the maximum length");
        }
    }

    private static <T> T require(T value, DomainViolation violation, String message) {
        if (value == null) {
            throw new DomainValidationException(violation, message);
        }
        return value;
    }

    /**
     * Fluent builder for {@link PartyIdentifier} instances.
     */
    public static final class Builder {

        private PartyIdentifierId identifierId;
        private TenantId tenantId;
        private PartyId partyId;
        private IdentifierSchemeId identifierSchemeId;
        private ProtectedIdentifierValue protectedValue;
        private String issuerCode;
        private LocalDate issuedOn;
        private LocalDate expiresOn;
        private boolean primary;
        private PartyIdentifierStatus status;
        private Instant verifiedAt;
        private String verifiedBy;
        private PartyIdentifierVersion version;
        private AuditInfo auditInfo;

        private Builder() {
        }

        public Builder identifierId(PartyIdentifierId identifierId) {
            this.identifierId = identifierId;
            return this;
        }

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder partyId(PartyId partyId) {
            this.partyId = partyId;
            return this;
        }

        public Builder identifierSchemeId(IdentifierSchemeId identifierSchemeId) {
            this.identifierSchemeId = identifierSchemeId;
            return this;
        }

        public Builder protectedValue(ProtectedIdentifierValue protectedValue) {
            this.protectedValue = protectedValue;
            return this;
        }

        public Builder issuerCode(String issuerCode) {
            this.issuerCode = issuerCode;
            return this;
        }

        public Builder issuedOn(LocalDate issuedOn) {
            this.issuedOn = issuedOn;
            return this;
        }

        public Builder expiresOn(LocalDate expiresOn) {
            this.expiresOn = expiresOn;
            return this;
        }

        public Builder primary(boolean primary) {
            this.primary = primary;
            return this;
        }

        public Builder status(PartyIdentifierStatus status) {
            this.status = status;
            return this;
        }

        public Builder verifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
            return this;
        }

        public Builder verifiedBy(String verifiedBy) {
            this.verifiedBy = verifiedBy;
            return this;
        }

        public Builder version(PartyIdentifierVersion version) {
            this.version = version;
            return this;
        }

        public Builder auditInfo(AuditInfo auditInfo) {
            this.auditInfo = auditInfo;
            return this;
        }

        /**
         * Sets initial lifecycle status, version, and audit information for a newly created identifier.
         *
         * @param occurredAt creation timestamp
         * @param createdBy  creating user
         * @return this builder
         */
        public Builder created(Instant occurredAt, String createdBy) {
            this.status = PartyIdentifierStatus.PENDING_VERIFICATION;
            this.version = PartyIdentifierVersion.initial();
            this.auditInfo = AuditInfo.initial(occurredAt, createdBy);
            return this;
        }

        /**
         * Validates invariants and constructs the immutable {@link PartyIdentifier}.
         *
         * @return a valid {@link PartyIdentifier} instance
         * @throws DomainValidationException if any aggregate invariant is violated
         */
        public PartyIdentifier build() {
            return new PartyIdentifier(this);
        }
    }
}
