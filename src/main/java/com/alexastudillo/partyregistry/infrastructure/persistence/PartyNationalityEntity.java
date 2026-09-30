package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Maps an independently stored nationality without a tenant-unsafe parent association. */
@Entity
@Table(name = "party_nationalities")
public class PartyNationalityEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected PartyNationalityEntity() {
    }

    PartyNationalityEntity(PartyNationality nationality) {
        this.id = nationality.nationalityId().value();
        this.partyId = nationality.partyId().value();
        this.countryCode = nationality.countryCode();
        this.primary = nationality.primary();
        this.validFrom = nationality.period().validFrom();
        this.validUntil = nationality.period().validUntil();
        this.createdAt = nationality.auditInfo().createdAt();
        this.createdBy = nationality.auditInfo().createdBy();
        this.updatedAt = nationality.auditInfo().updatedAt();
        this.updatedBy = nationality.auditInfo().updatedBy();
    }

    UUID id() {
        return id;
    }

    UUID partyId() {
        return partyId;
    }

    String countryCode() {
        return countryCode;
    }

    boolean primary() {
        return primary;
    }

    LocalDate validFrom() {
        return validFrom;
    }

    LocalDate validUntil() {
        return validUntil;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    PartyNationality toDomain() {
        return new PartyNationality(new NationalityId(id), new PartyId(partyId), countryCode, primary,
                new NationalityPeriod(validFrom, validUntil),
                new AuditInfo(createdAt, createdBy, updatedAt, updatedBy));
    }

    NationalityResult toResult() {
        return NationalityResult.fromAggregate(toDomain());
    }

    void changePeriod(NationalityPeriod period, Instant at, String userId) {
        validFrom = period.validFrom();
        validUntil = period.validUntil();
        updatedAt = at;
        updatedBy = userId;
    }

    void changePrimary(boolean value, Instant at, String userId) {
        primary = value;
        updatedAt = at;
        updatedBy = userId;
    }
}
