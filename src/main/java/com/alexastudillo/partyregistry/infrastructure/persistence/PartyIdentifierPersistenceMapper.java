package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Maps independent PartyIdentifier aggregates to and from protected persistence rows.
 */
@ApplicationScoped
public class PartyIdentifierPersistenceMapper {

    PartyIdentifierEntity toEntity(PartyIdentifier identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return PartyIdentifierEntity.builder()
                .id(identifier.identifierId().value())
                .tenantId(identifier.tenantId().value())
                .partyId(identifier.partyId().value())
                .identifierSchemeId(identifier.identifierSchemeId().value())
                .issuerCode(identifier.issuerCode())
                .protectedValue(identifier.protectedValue())
                .primary(identifier.isPrimary())
                .status(identifier.status())
                .issuedOn(identifier.issuedOn())
                .expiresOn(identifier.expiresOn())
                .verifiedAt(identifier.verifiedAt())
                .verifiedBy(identifier.verifiedBy())
                .auditInfo(identifier.auditInfo())
                .version(identifier.version().value())
                .build();
    }

    PartyIdentifier toDomain(PartyIdentifierEntity entity) {
        Objects.requireNonNull(entity, "entity");
        return PartyIdentifier.builder()
                .identifierId(new PartyIdentifierId(entity.id()))
                .tenantId(new TenantId(entity.tenantId()))
                .partyId(new PartyId(entity.partyId()))
                .identifierSchemeId(new IdentifierSchemeId(entity.identifierSchemeId()))
                .protectedValue(new ProtectedIdentifierValue(
                        entity.encryptedValue(),
                        entity.encryptionKeyVersion(),
                        entity.normalizedValueHash(),
                        entity.maskedValue(),
                        new IdentifierRuleVersion(entity.normalizationVersion())))
                .issuerCode(entity.issuerCode())
                .issuedOn(entity.issuedOn())
                .expiresOn(entity.expiresOn())
                .primary(entity.isPrimary())
                .status(entity.status())
                .verifiedAt(entity.verifiedAt())
                .verifiedBy(entity.verifiedBy())
                .version(new PartyIdentifierVersion(entity.version()))
                .auditInfo(new AuditInfo(
                        entity.createdAt(),
                        entity.createdBy(),
                        entity.updatedAt(),
                        entity.updatedBy()))
                .build();
    }
}
