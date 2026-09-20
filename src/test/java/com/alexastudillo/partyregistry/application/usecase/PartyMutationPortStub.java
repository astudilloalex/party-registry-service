package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationEvidence;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Records Application choreography through inner contracts, leaving transaction mechanics to the real adapter tests. */
final class PartyMutationPortStub implements PartyMutationPort, PartyMutationContext {

    final List<String> calls = new ArrayList<>();
    final List<OutboxEventCandidate> events = new ArrayList<>();
    Optional<Party> current = Optional.empty();
    Optional<CompletedPartyLifecycle> completion = Optional.empty();
    List<PartyActivationEvidence> evidence = List.of();
    RequestMetadata metadata;
    Party candidate;
    PartyVersion expectedVersion;
    Throwable writeFailure;
    Throwable entryFailure;
    PartyLifecycleAction action;
    String key;

    @Override
    public Uni<PartyMutationOutcome> execute(RequestMetadata request, Function<PartyMutationContext, Uni<PartyMutationOutcome>> work) {
        return Uni.createFrom().deferred(() -> {
            metadata = request;
            calls.add("begin");
            return entryFailure == null ? Uni.createFrom().deferred(() -> work.apply(this)) : Uni.createFrom().failure(entryFailure);
        }).invoke(outcome -> {
            calls.add("commit");
            if (outcome.disposition() == PartyMutationOutcome.Disposition.APPLIED) {
                current = Optional.ofNullable(candidate);
            }
        }).onFailure().invoke(() -> calls.add("rollback"));
    }

    @Override
    public TenantId tenantId() {
        return metadata.tenantId();
    }

    @Override
    public Uni<Void> serializeReplayKey(PartyLifecycleAction requested, String requestedKey) {
        calls.add("serialize");
        action = requested;
        key = requestedKey;
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Optional<CompletedPartyLifecycle>> findCompleted(PartyLifecycleAction requested, String requestedKey) {
        calls.add("completion");
        action = requested;
        key = requestedKey;
        return Uni.createFrom().item(completion);
    }

    @Override
    public Uni<Optional<Party>> findForUpdate(PartyId partyId) {
        calls.add("root");
        return Uni.createFrom().item(current.filter(party -> party.tenantId().equals(metadata.tenantId()) && party.partyId().equals(partyId)));
    }

    @Override
    public Uni<List<PartyActivationEvidence>> activationEvidence(PartyId partyId) {
        calls.add("evidence");
        return Uni.createFrom().item(evidence);
    }

    @Override
    public Uni<Party> persistRoot(Party proposed, PartyVersion expected) {
        calls.add("write");
        candidate = proposed;
        expectedVersion = expected;
        return writeFailure == null ? Uni.createFrom().item(proposed) : Uni.createFrom().failure(writeFailure);
    }

    @Override
    public Uni<Void> recordCompletion(PartyLifecycleAction requested, String requestedKey, CompletedPartyLifecycle outcome) {
        calls.add("record");
        action = requested;
        key = requestedKey;
        completion = Optional.of(outcome);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) {
        calls.add("event");
        events.add(event);
        return Uni.createFrom().voidItem();
    }
}
