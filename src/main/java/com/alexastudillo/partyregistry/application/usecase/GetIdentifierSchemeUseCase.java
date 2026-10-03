package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSelector;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.query.GetIdentifierSchemeQuery;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Resolves typed global selectors and converts catalog absence into an administration-specific failure. */
public final class GetIdentifierSchemeUseCase {
    private final IdentifierSchemeReadPort reads;
    private final OperationObservationPort observation;

    public GetIdentifierSchemeUseCase(IdentifierSchemeReadPort reads) {
        this(reads, OperationObservation.noop());
    }

    /** Composes global retrieval with subscription-scoped neutral observation. */
    public GetIdentifierSchemeUseCase(IdentifierSchemeReadPort reads, OperationObservationPort observation) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Retrieves current detached configuration in every lifecycle state without altering stored audit information. */
    public Uni<IdentifierSchemeResult> execute(GetIdentifierSchemeQuery query) {
        Objects.requireNonNull(query, "query");
        return OperationObservation.observe(query.requestMetadata(), ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_RETRIEVAL, observation,
                () -> (switch (query.selector()) {
                    case IdentifierSchemeSelector.ById(var id) -> reads.findById(id);
                    case IdentifierSchemeSelector.ByCode(var code) -> reads.findByCode(code);
                }).map(found -> found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierSchemeNotFound()))),
                ignored -> OperationOutcome.RETRIEVED);
    }
}
