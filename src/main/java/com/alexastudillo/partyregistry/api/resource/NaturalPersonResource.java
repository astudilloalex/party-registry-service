package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.contract.CommonResponseCode;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.NaturalPersonApiErrorTranslator;
import com.alexastudillo.partyregistry.api.error.NaturalPersonResponseCode;
import com.alexastudillo.partyregistry.api.mapper.NaturalPersonApiMapper;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPutRequest;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.command.GetNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.command.PatchNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.command.ReplaceNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.usecase.CreateNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.ReplaceNaturalPersonUseCase;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;
import java.util.UUID;

/**
 * Exposes reactive natural-person operations through the approved REST
 * contract.
 */
@Path("/v1/natural-person")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class NaturalPersonResource {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IF_MATCH_HEADER = "If-Match";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final CreateNaturalPersonUseCase createUseCase;
    private final GetNaturalPersonUseCase getUseCase;
    private final ReplaceNaturalPersonUseCase replaceUseCase;
    private final PatchNaturalPersonUseCase patchUseCase;
    private final RequestMetadataContext metadataContext;
    private final NaturalPersonApiMapper mapper;
    private final NaturalPersonApiErrorTranslator errorTranslator;
    private final ResponseManager responseManager;
    private final Validator validator;

    @Inject
    public NaturalPersonResource(
            CreateNaturalPersonUseCase createUseCase,
            GetNaturalPersonUseCase getUseCase,
            ReplaceNaturalPersonUseCase replaceUseCase,
            PatchNaturalPersonUseCase patchUseCase,
            RequestMetadataContext metadataContext,
            NaturalPersonApiMapper mapper,
            NaturalPersonApiErrorTranslator errorTranslator,
            ResponseManager responseManager,
            Validator validator) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.replaceUseCase = replaceUseCase;
        this.patchUseCase = patchUseCase;
        this.metadataContext = metadataContext;
        this.mapper = mapper;
        this.errorTranslator = errorTranslator;
        this.responseManager = responseManager;
        this.validator = validator;
    }

    /**
     * Creates or replays a natural person under one tenant and idempotency key.
     *
     * @param request strict creation body
     * @param headers raw request headers used to enforce exact key cardinality
     * @return reactive `201 successful` envelope
     */
    @POST
    @WithSpan("natural-person.create")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> createNaturalPerson(
            NaturalPersonCreateRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> createCommand(request, headers))
                .flatMap(createUseCase::execute)
                .invoke(result -> metadataContext.recordIdempotencyOutcome(result.outcome()))
                .map(result -> mapper.toResponse(result.result()))
                .map(response -> responseManager.customHttp(NaturalPersonResponseCode.CREATED, response))
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Retrieves one tenant-scoped natural person.
     *
     * @param partyId raw path identifier
     * @return reactive `200 successful` envelope
     */
    @GET
    @Path("/{partyId}")
    @WithSpan("natural-person.retrieve")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> getNaturalPerson(
            @PathParam("partyId") String partyId) {
        return Uni.createFrom().item(() -> new GetNaturalPersonCommand(
                metadataContext.metadata(),
                parsePartyId(partyId)))
                .flatMap(getUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Replaces every natural-person detail field using optimistic concurrency.
     *
     * @param partyId raw path identifier
     * @param request complete replacement body
     * @param headers raw request headers used to enforce exact precondition
     *                cardinality
     * @return reactive `200 successful` envelope
     */
    @PUT
    @Path("/{partyId}")
    @WithSpan("natural-person.replace")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> replaceNaturalPerson(
            @PathParam("partyId") String partyId,
            NaturalPersonPutRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> replaceCommand(partyId, request, headers))
                .flatMap(replaceUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Applies only supplied natural-person detail fields using optimistic
     * concurrency.
     *
     * @param partyId raw path identifier
     * @param request presence-aware patch body
     * @param headers raw request headers used to enforce exact precondition
     *                cardinality
     * @return reactive `200 successful` envelope
     */
    @PATCH
    @Path("/{partyId}")
    @WithSpan("natural-person.patch")
    public Uni<RestResponse<ApiResponse<NaturalPersonResponse>>> patchNaturalPerson(
            @PathParam("partyId") String partyId,
            NaturalPersonPatchRequest request,
            @Context HttpHeaders headers) {
        return Uni.createFrom().item(() -> patchCommand(partyId, request, headers))
                .flatMap(patchUseCase::execute)
                .map(mapper::toResponse)
                .map(responseManager::successHttp)
                .onFailure().transform(errorTranslator::translate);
    }

    /**
     * Routes strict JSON binding failures through the shared bad-request
     * envelope for this resource.
     *
     * @param ignoredFailure Jackson request binding failure
     * @return standard `400 bad-request` response
     */
    @ServerExceptionMapper
    public RestResponse<ApiResponse<Void>> mapInvalidJson(MismatchedInputException ignoredFailure) {
        return responseManager.errorHttp(CommonResponseCode.BAD_REQUEST);
    }

    private CreateNaturalPersonCommand createCommand(
            NaturalPersonCreateRequest request,
            HttpHeaders headers) {
        NaturalPersonCreateRequest validRequest = validate(request);
        String idempotencyKey = requireSingleHeader(headers, IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey.isBlank()
                || idempotencyKey.codePointCount(0, idempotencyKey.length()) > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest();
        }
        return new CreateNaturalPersonCommand(
                metadataContext.metadata(),
                idempotencyKey,
                validRequest.displayName(),
                validRequest.givenNames(),
                validRequest.familyNames(),
                validRequest.preferredName(),
                validRequest.birthDate(),
                validRequest.dateOfDeath(),
                validRequest.birthCountryCode());
    }

    private ReplaceNaturalPersonCommand replaceCommand(
            String partyId,
            NaturalPersonPutRequest request,
            HttpHeaders headers) {
        NaturalPersonPutRequest validRequest = validate(request);
        return new ReplaceNaturalPersonCommand(
                metadataContext.metadata(),
                parsePartyId(partyId),
                parseExpectedVersion(headers),
                validRequest.givenNames(),
                validRequest.familyNames(),
                validRequest.preferredName(),
                validRequest.birthDate(),
                validRequest.dateOfDeath(),
                validRequest.birthCountryCode());
    }

    private PatchNaturalPersonCommand patchCommand(
            String partyId,
            NaturalPersonPatchRequest request,
            HttpHeaders headers) {
        NaturalPersonPatchRequest validRequest = validate(request);
        return new PatchNaturalPersonCommand(
                metadataContext.metadata(),
                parsePartyId(partyId),
                parseExpectedVersion(headers),
                validRequest.toPatch());
    }

    private <T> T validate(T request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw badRequest();
        }
        return request;
    }

    private static PartyId parsePartyId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw badRequest();
            }
            return new PartyId(parsed);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private static PartyVersion parseExpectedVersion(HttpHeaders headers) {
        String value = requireSingleHeader(headers, IF_MATCH_HEADER);
        if (!value.matches("^(0|[1-9]\\d*)$")) {
            throw badRequest();
        }
        try {
            return new PartyVersion(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw badRequest(exception);
        }
    }

    private static String requireSingleHeader(HttpHeaders headers, String name) {
        List<String> values = headers.getRequestHeader(name);
        if (values == null || values.size() != 1 || values.getFirst() == null) {
            throw badRequest();
        }
        return values.getFirst();
    }

    private static ApiResponseException badRequest() {
        return new ApiResponseException(CommonResponseCode.BAD_REQUEST);
    }

    private static ApiResponseException badRequest(Throwable cause) {
        return new ApiResponseException(CommonResponseCode.BAD_REQUEST, cause);
    }
}
