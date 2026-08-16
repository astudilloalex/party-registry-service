package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.OutboxClaim;
import com.alexastudillo.partyregistry.application.PublicationOutcome;
import java.util.concurrent.CompletionStage;

public interface IntegrationEventPublisherPort {
    CompletionStage<PublicationOutcome> publish(OutboxClaim event);
}
