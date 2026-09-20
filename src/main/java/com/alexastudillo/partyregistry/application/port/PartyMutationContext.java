package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationEvidence;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;

/**
 * Exposes sequential Party persistence capabilities tied to one active transaction and immutable tenant.
 */
public interface PartyMutationContext {

    TenantId tenantId();

    /** Serializes the complete tenant/action/key scope before any completed-result lookup or Party lock. */
    Uni<Void> serializeReplayKey(PartyLifecycleAction action, String key);

    /** Reads the full-scope completed result after replay-key serialization. */
    Uni<Optional<CompletedPartyLifecycle>> findCompleted(PartyLifecycleAction action, String key);

    /** Locks and restores one tenant-qualified Party, concealing missing and cross-tenant roots equally. */
    Uni<Optional<Party>> findForUpdate(PartyId partyId);

    /** Reads minimal eligibility inputs in this scope without loading protected identifier material. */
    Uni<List<PartyActivationEvidence>> activationEvidence(PartyId partyId);

    /**
     * Persists only the requested root correction/transition, guarded by the bound tenant and expected version.
     *
     * @param candidate Domain-validated aggregate at exactly the next version with original details retained
     * @param expectedVersion version of the locked root
     * @return the reloaded root and safe matching details, without an additional version increment
     */
    Uni<Party> persistRoot(Party candidate, PartyVersion expectedVersion);

    /** Stores the original safe success in the already-serialized tenant/action/key scope. */
    Uni<Void> recordCompletion(PartyLifecycleAction action, String key, CompletedPartyLifecycle outcome);

    /** Appends allowlisted event intent when configured, atomically with the root and completion record. */
    Uni<Void> appendEnabledEvent(OutboxEventCandidate event);
}
