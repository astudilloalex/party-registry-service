package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import io.smallrye.mutiny.Uni;

import java.util.function.Function;

/** Wraps one Application-owned global catalog workflow in an atomic request-attributed scope. */
public interface IdentifierSchemeMutationPort {

    /**
     * Executes a lazy sequential workflow and emits its accepted outcome only after commit.
     * Failure or cancellation rolls back unaccepted writes and releases owned resources.
     *
     * @param metadata trusted attribution and tenant replay scope, never catalog ownership
     * @param work complete workflow whose context must not escape or execute concurrently
     * @return committed applied/replayed outcome or the original classified failure/cancellation
     */
    Uni<IdentifierSchemeMutationOutcome> execute(RequestMetadata metadata,
            Function<IdentifierSchemeMutationContext, Uni<IdentifierSchemeMutationOutcome>> work);
}
