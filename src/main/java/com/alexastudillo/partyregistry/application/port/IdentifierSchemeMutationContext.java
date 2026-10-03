package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Exposes sequential catalog capabilities valid only inside one active atomic mutation callback. */
public interface IdentifierSchemeMutationContext {

    /** Identifies replay scope; catalog rows remain global. */
    TenantId tenantId();

    /** Serializes full tenant/operation/key intent before completion lookup and any code or row lock. */
    Uni<Void> serializeReplayKey(String operation, String key);

    /** Resolves immutable historical success under the already serialized full replay identity. */
    Uni<Optional<CompletedIdentifierSchemeOperation>> findCompleted(String operation, String key);

    /** Serializes an exact global catalog code independently of requesting tenant. */
    Uni<Void> serializeCode(String code);

    /** Tests exact global code assignment, including deprecated and retired entries. */
    Uni<Boolean> codeExists(String code);

    /** Locks and restores a global scheme without a tenant ownership predicate. */
    Uni<Optional<IdentifierScheme>> findForUpdate(IdentifierSchemeId id);

    /** Inserts a Domain-validated draft at initial version zero within this scope. */
    Uni<IdentifierScheme> insert(IdentifierScheme candidate);

    /**
     * Writes a validated next-version candidate guarded by identity and locked expected version.
     *
     * @param candidate accepted prospective scheme retaining immutable identity and creation audit
     * @param expectedVersion version of the locked scheme
     * @return freshly reloaded accepted scheme without an additional automatic version increment
     */
    Uni<IdentifierScheme> persistScheme(IdentifierScheme candidate, IdentifierSchemeVersion expectedVersion);

    /** Stores original intent/result atomically with its accepted catalog mutation in the serialized key scope. */
    Uni<Void> recordCompletion(String operation, String key, CompletedIdentifierSchemeOperation completed);
}
