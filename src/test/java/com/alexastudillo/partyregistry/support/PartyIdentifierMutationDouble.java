package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Supplies recording creation-only transaction capabilities for policy/protection tests, without simulating durability. */
public final class PartyIdentifierMutationDouble implements PartyIdentifierMutationPort, PartyIdentifierMutationContext, PartyIdentifierFingerprintPort {
    public final List<IdentifierAcceptance> candidates = new ArrayList<>();
    public Function<IdentifierAcceptance, Uni<PartyIdentifierResult>> behavior = candidate ->
            Uni.createFrom().item(PartyIdentifierResult.fromAggregate(candidate.identifier(), candidate.identifierScheme()));
    private final List<String> order;
    private final BiFunction<TenantId, PartyId, Uni<Optional<PartyType>>> owners;
    private final IdentifierSchemeRepository schemes;
    private RequestMetadata metadata;
    private PartyType partyType;
    private IdentifierScheme scheme;
    private String key;

    /** Adapts existing policy-test lookup doubles into a single recording callback scope. */
    public PartyIdentifierMutationDouble(List<String> order, BiFunction<TenantId, PartyId, Uni<Optional<PartyType>>> owners, IdentifierSchemeRepository schemes) {
        this.order = order; this.owners = owners; this.schemes = schemes;
    }

    @Override
    public Uni<PartyIdentifierMutationOutcome> execute(RequestMetadata request, Function<PartyIdentifierMutationContext, Uni<PartyIdentifierMutationOutcome>> work) {
        return Uni.createFrom().deferred(() -> { metadata = request; return work.apply(this); });
    }

    @Override public TenantId tenantId() { return metadata.tenantId(); }
    @Override public Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String requested) { key = requested; return Uni.createFrom().voidItem(); }
    @Override public Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String requested) { return Uni.createFrom().item(Optional.empty()); }
    @Override public Uni<Optional<PartyType>> findOwnedParty(PartyId id) {
        return owners.apply(metadata.tenantId(), id).invoke(found -> partyType = found.orElse(null));
    }
    @Override public Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String code) {
        return schemes.findByCode(code).invoke(found -> scheme = found.orElse(null)).map(found -> found.map(IdentifierScheme::id));
    }
    @Override public Uni<Void> serializeScope(PartyId id, IdentifierSchemeId schemeId) { return Uni.createFrom().voidItem(); }
    @Override public Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId id) { return Uni.createFrom().item(Optional.ofNullable(scheme)); }
    @Override public Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier identifier) {
        order.add("registerIdentifier");
        var candidate = new IdentifierAcceptance(metadata, key, Objects.requireNonNull(partyType), identifier, Objects.requireNonNull(scheme), List.of());
        candidates.add(candidate);
        return behavior.apply(candidate);
    }
    @Override public Uni<Void> recordCompletion(PartyIdentifierOperation operation, String requested, CompletedPartyIdentifierMutation completion) { return Uni.createFrom().voidItem(); }
    @Override public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) {
        var original = candidates.getLast();
        candidates.set(candidates.size() - 1, new IdentifierAcceptance(original.requestMetadata(), original.idempotencyKey(), original.partyType(),
                original.identifier(), original.identifierScheme(), List.of(event)));
        return Uni.createFrom().voidItem();
    }
    @Override public String fingerprint(TenantId tenant, PartyIdentifierCreationRequest request) { return "a".repeat(64); }
    @Override public String fingerprint(TenantId tenant, PartyIdentifierActionRequest request) { throw unexpected(); }
    @Override public boolean matches(String stored, String candidate) { return stored.equals(candidate); }
    @Override public Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId id, PartyIdentifierId identifier) { throw unexpected(); }
    @Override public Uni<Optional<PartyIdentifier>> findForUpdate(PartyId id, PartyIdentifierId identifier) { throw unexpected(); }
    @Override public Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId id, IdentifierSchemeId schemeId) { throw unexpected(); }
    @Override public Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier candidate, PartyIdentifierVersion expected) { throw unexpected(); }
    @Override public Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit) { throw unexpected(); }
    private static AssertionError unexpected() { return new AssertionError("Creation policy tests must not mutate existing identifiers"); }

    /** Captures synthetic accepted-write inputs for assertions without restoring the retired production candidate contract. */
    public record IdentifierAcceptance(RequestMetadata requestMetadata, String idempotencyKey, PartyType partyType,
            PartyIdentifier identifier, IdentifierScheme identifierScheme, List<OutboxEventCandidate> outboxCandidates) {
        public IdentifierAcceptance { outboxCandidates = List.copyOf(outboxCandidates); }
        @Override public String toString() { return "IdentifierAcceptance[synthetic input redacted]"; }
    }
}
