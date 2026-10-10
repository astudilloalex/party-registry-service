package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import io.smallrye.mutiny.Uni;

import java.util.function.Function;

/** Owns one reactive transaction capability and emits only its safe complete outcome after commit. */
@FunctionalInterface
public interface PartyIdentifierMutationPort {

    /**
     * Executes sequential application-owned work with a fresh tenant-bound context and owned cleanup.
     *
     * @param metadata accepted request tenant and attribution
     * @param work sequential workflow; no context may escape, overlap work, or be reused after termination
     * @return safe applied/replayed result only after complete acceptance; failures/cancellation preserve rollback
     */
    Uni<PartyIdentifierMutationOutcome> execute(RequestMetadata metadata,
            Function<PartyIdentifierMutationContext, Uni<PartyIdentifierMutationOutcome>> work);
}
