package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Supplies immutable read fixtures to real use cases and fails immediately if a rejection attempts a write. */
final class IdentifierSchemeBoundaryMutationContext implements IdentifierSchemeMutationContext {
    private final TenantId tenant;
    private final Optional<IdentifierScheme> scheme;
    private final boolean codeExists;

    IdentifierSchemeBoundaryMutationContext(TenantId tenant, Optional<IdentifierScheme> scheme, boolean codeExists) {
        this.tenant = tenant;
        this.scheme = scheme;
        this.codeExists = codeExists;
    }

    @Override
    public TenantId tenantId() {
        return tenant;
    }

    @Override
    public Uni<Void> serializeReplayKey(String operation, String key) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Optional<CompletedIdentifierSchemeOperation>> findCompleted(String operation, String key) {
        return Uni.createFrom().item(Optional.empty());
    }

    @Override
    public Uni<Void> serializeCode(String code) {
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Boolean> codeExists(String code) {
        return Uni.createFrom().item(codeExists);
    }

    @Override
    public Uni<Optional<IdentifierScheme>> findForUpdate(IdentifierSchemeId id) {
        return Uni.createFrom().item(scheme);
    }

    @Override
    public Uni<IdentifierScheme> insert(IdentifierScheme candidate) {
        return forbiddenWrite();
    }

    @Override
    public Uni<IdentifierScheme> persistScheme(IdentifierScheme candidate, IdentifierSchemeVersion expectedVersion) {
        return forbiddenWrite();
    }

    @Override
    public Uni<Void> recordCompletion(String operation, String key, CompletedIdentifierSchemeOperation completed) {
        return forbiddenWrite();
    }

    private static <T> Uni<T> forbiddenWrite() {
        return Uni.createFrom().failure(new AssertionError("A rejected boundary fixture must not write"));
    }
}
