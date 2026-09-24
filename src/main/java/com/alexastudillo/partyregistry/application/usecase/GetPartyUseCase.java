package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.query.GetPartyQuery;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Resolves the safe common Party detail or a tenant-concealing absence failure without identifier or geographic queries. */
public final class GetPartyUseCase {

    private final PartyQueryPort queries;
    private final OperationObservationPort observations;

    public GetPartyUseCase(PartyQueryPort queries, OperationObservationPort observations) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    /** Retrieves current stored details in any lifecycle state and maps qualified absence to PartyNotFound. */
    public Uni<PartyDetailsResult> execute(GetPartyQuery query) {
        Objects.requireNonNull(query, "query");
        return OperationObservation.observe(query.metadata(), ObservedOperation.APPLICATION_PARTY_RETRIEVAL, observations,
                () -> queries.findDetails(query.metadata().tenantId(), query.partyId())
                .map(found -> found.orElseThrow(() -> new ApplicationException(
                        new ApplicationFailure.PartyNotFound(query.partyId(), query.metadata().tenantId())))),
                ignored -> OperationOutcome.RETRIEVED);
    }
}
