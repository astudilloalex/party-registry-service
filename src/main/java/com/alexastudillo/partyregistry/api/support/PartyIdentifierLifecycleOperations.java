package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyIdentifierLifecycleUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestResponse;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Adapts buffered lifecycle syntax and safe committed Application results to the shared HTTP boundary. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierLifecycleOperations {
    private final ChangePartyIdentifierLifecycleUseCase lifecycle;
    private final RequestMetadataContext context;
    private final PartyIdentifierApiMapper mapper;
    private final PartyApiErrorTranslator failures;
    private final ResponseManager responses;
    private final PartyIdentifierRequestSupport inputs;
    private final ObjectMapper json;

    @Inject
    public PartyIdentifierLifecycleOperations(ChangePartyIdentifierLifecycleUseCase lifecycle, RequestMetadataContext context,
            PartyIdentifierApiMapper mapper, PartyApiErrorTranslator failures, ResponseManager responses,
            PartyIdentifierRequestSupport inputs, ObjectMapper json) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.context = Objects.requireNonNull(context, "context");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.json = Objects.requireNonNull(json, "json");
    }

    /** Validates actual buffered body bytes before selectors, version and key, then delegates the entire action. */
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> change(String party, String identifier,
            @Nullable byte[] body, HttpHeaders headers, PartyIdentifierOperation operation) {
        return Uni.createFrom().item(() -> {
            PartyIdentifierReason reason;
            if (operation == PartyIdentifierOperation.VERIFY) {
                PartyIdentifierActionBodies.verify(body);
                reason = PartyIdentifierReason.none();
            } else {
                var request = PartyIdentifierActionBodies.reasons(body, json);
                reason = new PartyIdentifierReason(request.reasonCode(), request.reasonDetail());
            }
            return inputs.lifecycleCommand(context.metadata(), party, identifier, headers, operation, reason);
        }).flatMap(lifecycle::execute)
                .map(outcome -> mapper.toResponse(outcome.identifier())).map(responses::successHttp)
                .onFailure().transform(failures::translate);
    }
}
