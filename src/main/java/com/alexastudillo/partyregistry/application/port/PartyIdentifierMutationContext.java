package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Exposes sequential tenant-qualified capabilities in one active transaction, never sessions, entities or SQL. */
public interface PartyIdentifierMutationContext {

    TenantId tenantId();

    /** Serializes exactly one full tenant/operation/key tuple before completion or owner access. */
    Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String key);

    /** Reads only the full serialized completion tuple; a hit prohibits subsequent new-operation writes. */
    Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String key);

    /** Read-locks the tenant-qualified owner without changing its version and returns its immutable type. */
    Uni<Optional<PartyType>> findOwnedParty(PartyId partyId);

    /** Resolves an owned target's immutable scheme before scope serialization, without making version/state decisions. */
    Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId partyId, PartyIdentifierId identifierId);

    /** Resolves exact creation scheme identity before its scoped registration lock. */
    Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String schemeCode);

    /** Serializes exactly one tenant/Party/scheme scope after owned Party access and before child row locks. */
    Uni<Void> serializeScope(PartyId partyId, IdentifierSchemeId schemeId);

    /** Read-locks and rechecks the creation scheme after scope serialization; eligibility remains an inner policy. */
    Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId schemeId);

    /** Freshly locks/restores one target in the bound owner/scope, concealing every wrong-owner identifier. */
    Uni<Optional<PartyIdentifier>> findForUpdate(PartyId partyId, PartyIdentifierId identifierId);

    /** Locks the stored verified primary, including past-date records, in the already serialized scope. */
    Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId partyId, IdentifierSchemeId schemeId);

    /** Inserts a Domain-validated new identifier and reloads its safe matching result. */
    Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier candidate);

    /** Performs a qualified expected-version write using the Domain-computed next version, then reloads safe state. */
    Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier candidate, PartyIdentifierVersion expectedVersion);

    /** Appends attributable private withdrawal audit independently of optional replay and outbox settings. */
    Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit);

    /** Stores only a full accepted safe result in the already serialized operation/key scope. */
    Uni<Void> recordCompletion(PartyIdentifierOperation operation, String key, CompletedPartyIdentifierMutation completion);

    /** Appends allowlisted matching event intent when enabled, without broker publication inside this transaction. */
    Uni<Void> appendEnabledEvent(OutboxEventCandidate event);
}
