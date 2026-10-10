package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.usecase.SetPrimaryPartyIdentifierUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestResponse;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Maps strict primary-selection syntax and safe committed results to the shared HTTP contract. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierPrimaryOperations {
    private final SetPrimaryPartyIdentifierUseCase primary;
    private final RequestMetadataContext context;
    private final PartyIdentifierApiMapper mapper;
    private final PartyApiErrorTranslator failures;
    private final ResponseManager responses;
    private final PartyIdentifierRequestSupport inputs;
    private final ObjectMapper json;

    @Inject
    public PartyIdentifierPrimaryOperations(SetPrimaryPartyIdentifierUseCase primary, RequestMetadataContext context,
            PartyIdentifierApiMapper mapper, PartyApiErrorTranslator failures, ResponseManager responses,
            PartyIdentifierRequestSupport inputs, ObjectMapper json) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.context = Objects.requireNonNull(context, "context");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.json = Objects.requireNonNull(json, "json");
    }

    /** Validates actual buffered optional body before selectors and headers, then delegates the complete selection. */
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> select(String party, String identifier,
            @Nullable byte[] body, HttpHeaders headers) {
        return Uni.createFrom().item(() -> {
            var request = PartyIdentifierActionBodies.primary(body, json);
            var command = inputs.primaryCommand(context.metadata(), party, identifier, request, headers);
            if (command.idempotencyKey() != null) { context.markIdentifierKeyed(); }
            return command;
        }).flatMap(primary::execute).invoke(context::recordIdentifierDisposition)
                .map(outcome -> mapper.toResponse(outcome.identifier())).map(responses::successHttp)
                .onFailure().transform(failures::translate);
    }
}
