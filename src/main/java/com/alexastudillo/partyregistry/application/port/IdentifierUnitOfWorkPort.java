package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.IdentifierMutationIntent;
import com.alexastudillo.partyregistry.application.MutationResult;
import java.util.concurrent.CompletionStage;

public interface IdentifierUnitOfWorkPort {
    CompletionStage<MutationResult> createIdentifierAndAppendOutbox(IdentifierMutationIntent intent);

    CompletionStage<MutationResult> updateIdentifierAndAppendOutbox(IdentifierMutationIntent intent);

    CompletionStage<MutationResult> transitionIdentifierAndAppendOutbox(IdentifierMutationIntent intent);
}
