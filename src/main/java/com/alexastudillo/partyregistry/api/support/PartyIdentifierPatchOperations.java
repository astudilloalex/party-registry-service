package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPatchRequest;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.usecase.PatchPartyIdentifierUseCase;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.Objects;

/** Adapts ordered metadata commands and accepted safe outcomes to the shared HTTP contract without business/persistence decisions. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierPatchOperations {
    private final PatchPartyIdentifierUseCase patch;
    private final RequestMetadataContext context;
    private final PartyIdentifierApiMapper mapper;
    private final PartyApiErrorTranslator failures;
    private final ResponseManager responses;
    private final PartyIdentifierRequestSupport inputs;

    @Inject
    public PartyIdentifierPatchOperations(PatchPartyIdentifierUseCase patch, RequestMetadataContext context, PartyIdentifierApiMapper mapper,
            PartyApiErrorTranslator failures, ResponseManager responses, PartyIdentifierRequestSupport inputs) {
        this.patch = Objects.requireNonNull(patch, "patch"); this.context = Objects.requireNonNull(context, "context");
        this.mapper = Objects.requireNonNull(mapper, "mapper"); this.failures = Objects.requireNonNull(failures, "failures");
        this.responses = Objects.requireNonNull(responses, "responses"); this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    /** Applies only validated current-version metadata correction, ignoring all replay headers. */
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> patch(String party, String identifier, PartyIdentifierPatchRequest request, HttpHeaders headers) {
        return Uni.createFrom().item(() -> inputs.patchCommand(context.metadata(), party, identifier, request, headers))
                .flatMap(patch::execute).map(outcome -> mapper.toResponse(outcome.identifier())).map(responses::successHttp)
                .onFailure().transform(failures::translate);
    }
}
