package com.alexastudillo.partyregistry.adapter.out.postgresql;

import com.alexastudillo.partyregistry.application.PageRequest;
import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.PartyIdentifierView;
import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Separate implementation because Party and Identifier query ports have an incompatible erased method signature. */
public final class PostgreSqlIdentifierQueryAdapter implements IdentifierQueryPort {
    private final PostgreSqlPartyRegistryAdapter delegate;

    PostgreSqlIdentifierQueryAdapter(PostgreSqlPartyRegistryAdapter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public CompletionStage<PartyIdentifierView> findByTenantAndId(UUID tenantId, UUID identifierId) {
        return delegate.findIdentifierByTenantAndId(tenantId, identifierId);
    }

    @Override
    public CompletionStage<PartyIdentifierView> findProtectedByTenantAndId(UUID tenantId, UUID identifierId) {
        return delegate.findProtectedIdentifierByTenantAndId(tenantId, identifierId);
    }

    @Override
    public CompletionStage<List<PartyIdentifierView>> findExact(
            UUID tenantId, UUID schemeId, String normalizedValueHash) {
        return delegate.findExactIdentifier(tenantId, schemeId, normalizedValueHash);
    }

    @Override
    public CompletionStage<PageResult<PartyIdentifierView>> search(
            UUID tenantId,
            UUID partyId,
            UUID schemeId,
            PartyIdentifierStatus status,
            Boolean primary,
            PageRequest pageRequest) {
        return delegate.searchIdentifiers(tenantId, partyId, schemeId, status, primary, pageRequest);
    }

    @Override
    public CompletionStage<List<PartyIdentifierView>> findByPartyAndScheme(
            UUID tenantId, UUID partyId, UUID schemeId, boolean verifiedPrimaryOnly) {
        return delegate.findIdentifiersByPartyAndScheme(tenantId, partyId, schemeId, verifiedPrimaryOnly);
    }
}
