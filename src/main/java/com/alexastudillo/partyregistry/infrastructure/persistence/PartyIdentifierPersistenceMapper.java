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
        return new PartyIdentifierEntity(
                identifier.identifierId().value(),
                identifier.tenantId().value(),
                identifier.partyId().value(),
                identifier.identifierSchemeId().value(),
                identifier.issuerCode(),
                identifier.protectedValue(),
                identifier.isPrimary(),
                identifier.status(),
                identifier.issuedOn(),
                identifier.expiresOn(),
                identifier.verifiedAt(),
                identifier.verifiedBy(),
                identifier.auditInfo(),
                identifier.version().value());
    }

    PartyIdentifier toDomain(PartyIdentifierEntity entity) {
        Objects.requireNonNull(entity, "entity");
        return PartyIdentifier.restore(
                new PartyIdentifierId(entity.id()),
                new TenantId(entity.tenantId()),
                new PartyId(entity.partyId()),
                new IdentifierSchemeId(entity.identifierSchemeId()),
                new ProtectedIdentifierValue(
                        entity.encryptedValue(),
                        entity.encryptionKeyVersion(),
                        entity.normalizedValueHash(),
                        entity.maskedValue(),
                        new IdentifierRuleVersion(entity.normalizationVersion())),
                entity.issuerCode(),
                entity.issuedOn(),
                entity.expiresOn(),
                entity.isPrimary(),
                entity.status(),
                entity.verifiedAt(),
                entity.verifiedBy(),
                new PartyIdentifierVersion(entity.version()),
                new AuditInfo(
                        entity.createdAt(),
                        entity.createdBy(),
                        entity.updatedAt(),
                        entity.updatedBy()));
    }
}
