package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyApiErrorTranslator;
import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.usecase.GetPartyIdentifierUseCase;
import com.alexastudillo.partyregistry.application.usecase.ListPartyIdentifiersUseCase;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adapts identifier query inputs, Application results and known failures to explicit shared HTTP responses. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierQueryOperations {

    private final GetPartyIdentifierUseCase detail;
    private final ListPartyIdentifiersUseCase listing;
    private final RequestMetadataContext context;
    private final PartyIdentifierApiMapper mapper;
    private final PartyApiErrorTranslator failures;
    private final ResponseManager responses;
    private final PartyIdentifierRequestSupport inputs;

    @Inject
    public PartyIdentifierQueryOperations(GetPartyIdentifierUseCase detail, ListPartyIdentifiersUseCase listing,
            RequestMetadataContext context, PartyIdentifierApiMapper mapper, PartyApiErrorTranslator failures,
            ResponseManager responses, PartyIdentifierRequestSupport inputs) {
        this.detail = Objects.requireNonNull(detail, "detail");
        this.listing = Objects.requireNonNull(listing, "listing");
        this.context = Objects.requireNonNull(context, "context");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    /** Maps an owned historical detail to the safe API DTO before wrapping its successful response. */
    public Uni<RestResponse<ApiResponse<PartyIdentifierResponse>>> detail(String party, String identifier) {
        return Uni.createFrom().item(() -> inputs.detailQuery(context.metadata(), party, identifier))
                .flatMap(detail::execute).map(mapper::toResponse).map(responses::successHttp)
                .onFailure().transform(failures::translate);
    }

    /** Validates the complete decoded query and wraps only detached API DTOs, never Application records. */
    public Uni<RestResponse<ApiResponse<List<PartyIdentifierResponse>>>> list(String party, Map<String, List<String>> parameters) {
        return Uni.createFrom().item(() -> inputs.listQuery(context.metadata(), party, parameters))
                .flatMap(listing::execute).map(page -> responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page)))
                .onFailure().transform(failures::translate);
    }
}
