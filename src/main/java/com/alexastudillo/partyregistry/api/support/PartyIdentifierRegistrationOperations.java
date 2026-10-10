package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.Objects;

/** Keeps existing registration validation/mapping and safe created responses separate from independent query/PATCH delivery. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierRegistrationOperations {
    private final RegisterPartyIdentifierUseCase registration;
    private final RequestMetadataContext context;
    private final PartyIdentifierApiMapper mapper;
    private final PartyApiErrorTranslator failures;
    private final ResponseManager responses;
    private final ApiRequestSupport inputs;

    @Inject
    public PartyIdentifierRegistrationOperations(RegisterPartyIdentifierUseCase registration, RequestMetadataContext context,
            PartyIdentifierApiMapper mapper, PartyApiErrorTranslator failures, ResponseManager responses, ApiRequestSupport inputs) {
        this.registration = Objects.requireNonNull(registration, "registration"); this.context = Objects.requireNonNull(context, "context");
        this.mapper = Objects.requireNonNull(mapper, "mapper"); this.failures = Objects.requireNonNull(failures, "failures");
        this.responses = Objects.requireNonNull(responses, "responses"); this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    /** Preserves body→key→Party input order and maps Application output before CDI-managed 201 wrapping. */
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> register(String party, PartyIdentifierCreateRequest request, HttpHeaders headers) {
        return Uni.createFrom().item(() -> command(party, request, headers)).flatMap(registration::executeOutcome)
                .invoke(context::recordIdentifierDisposition).map(outcome -> mapper.toResponse(outcome.identifier())).map(dto -> responses.customHttp(PartyResponseCode.CREATED, dto))
                .onFailure().transform(failures::translate);
    }

    private RegisterPartyIdentifierCommand command(String party, PartyIdentifierCreateRequest request, HttpHeaders headers) {
        var body = inputs.validateBody(request);
        var key = inputs.requireIdempotencyKey(headers);
        var command = new RegisterPartyIdentifierCommand(context.metadata(), key, inputs.parsePartyId(party), mapper.toInput(body));
        context.markIdentifierKeyed();
        return command;
    }
}
