package com.alexastudillo.partyregistry.infrastructure.persistence.party.entity;

import java.time.Instant;
import java.util.UUID;

import com.alexastudillo.partyregistry.domain.party.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.party.model.PartyType;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "parties")
public class PartyEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "party_type")
    private PartyType type;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "record_status", nullable = false, columnDefinition = "party_record_status")
    private PartyRecordStatus recordStatus;

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

    public PartyEntity() {
    }

    public PartyEntity(
            UUID tenantId,
            PartyType type,
            String displayName,
            PartyRecordStatus recordStatus,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            long version) {
        this.tenantId = tenantId;
        this.type = type;
        this.displayName = displayName;
        this.recordStatus = recordStatus;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.version = version;
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }
}
