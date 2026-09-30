package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import io.smallrye.mutiny.Uni;

import java.util.function.Function;

/**
 * Wraps one Application-owned Party mutation workflow in an atomic tenant-bound persistence scope.
 */
public interface PartyMutationPort {

    /**
     * Executes sequential scoped operations and emits the accepted result only after commit.
     * Failure or cancellation aborts unaccepted writes and releases the scope's resources.
     *
     * @param metadata trusted tenant, actor, and correlation for this attempt
     * @param work complete lazy workflow; its context must not escape the returned pipeline
     * @return committed applied/replayed result, or the original classified failure/cancellation
     */
    Uni<PartyMutationOutcome> execute(
            RequestMetadata metadata, Function<PartyMutationContext, Uni<PartyMutationOutcome>> work);
}
