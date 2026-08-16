package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityMutationIntent;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import java.util.concurrent.CompletionStage;

public interface PartyUnitOfWorkPort {
    CompletionStage<MutationResult> createPartyAndAppendOutbox(PartyMutationIntent intent);

    CompletionStage<MutationResult> updatePartyAndAppendOutbox(PartyMutationIntent intent);

    CompletionStage<MutationResult> transitionPartyAndAppendOutbox(PartyMutationIntent intent);

    CompletionStage<MutationResult> updateDetailsAndAppendOutbox(PartyMutationIntent intent);

    CompletionStage<MutationResult> addNationalityAndAppendOutbox(NationalityMutationIntent intent);

    CompletionStage<MutationResult> updateNationalityAndAppendOutbox(NationalityMutationIntent intent);

    CompletionStage<MutationResult> endNationalityAndAppendOutbox(NationalityMutationIntent intent);
}
