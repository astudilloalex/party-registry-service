package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.mapper.IdentifierSchemeApiMapper;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSchemeResponse;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeRequestSupport;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.usecase.CreateIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Exercises existing neutral failures and real use-case rejection through the shared mapper in test sources only. */
@Path("/v1/identifier-scheme-error-verification")
@Produces(MediaType.APPLICATION_JSON)
public class IdentifierSchemeErrorVerificationResource {
    static final String ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    @Inject
    PartyApiErrorTranslator translator;
    @Inject
    IdentifierSchemeRequestSupport support;
    @Inject
    RequestMetadataContext metadata;
    @Inject
    IdentifierSchemeApiMapper mapper;
    @Inject
    ResponseManager responses;

    @GET
    @Path("/{scenario}")
    public Uni<RestResponse<ApiResponse<Void>>> failure(@PathParam("scenario") String scenario) {
        return Uni.createFrom().<RestResponse<ApiResponse<Void>>>failure(() -> failureFor(scenario))
                .onFailure().transform(translator::translate);
    }

    @POST
    @Path("/create/{scenario}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> create(IdentifierSchemeCreateRequest request,
            @PathParam("scenario") String scenario, @Context HttpHeaders headers) {
        return Uni.createFrom().deferred(() -> {
            var command = support.createCommand(metadata.metadata(), request, headers);
            var context = new IdentifierSchemeBoundaryMutationContext(metadata.metadata().tenantId(), Optional.empty(),
                    scenario.equals("code-conflict"));
            IdentifierSchemeMutationPort port = (_, workflow) -> workflow.apply(context);
            return new CreateIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), Clock.systemUTC()).execute(command);
        }).onFailure().transform(translator::translate)
                .map(outcome -> mapper.toResponse(outcome.scheme()))
                .map(dto -> responses.customHttp(PartyResponseCode.CREATED, dto));
    }

    @POST
    @Path("/patch/{scenario}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<ApiResponse<IdentifierSchemeResponse>>> patch(IdentifierSchemePatchRequest request,
            @PathParam("scenario") String scenario, @Context HttpHeaders headers) {
        return Uni.createFrom().deferred(() -> {
            var command = support.patchCommand(metadata.metadata(), request, ID, headers);
            var context = new IdentifierSchemeBoundaryMutationContext(metadata.metadata().tenantId(),
                    scenario.equals("absent") ? Optional.empty() : Optional.of(scheme(scenario)), false);
            IdentifierSchemeMutationPort port = (_, workflow) -> workflow.apply(context);
            return new PatchIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), Clock.systemUTC()).execute(command);
        }).onFailure().transform(translator::translate)
                .map(outcome -> mapper.toResponse(outcome.scheme())).map(responses::successHttp);
    }

    static RuntimeException failureFor(String scenario) {
        if (scenario.equals("unexpected")) {
            return new IllegalStateException("private SQL credentials stack detail");
        }
        ApplicationFailure failure = switch (scenario) {
            case "missing" -> new ApplicationFailure.IdentifierSchemeNotFound();
            case "code-conflict" -> new ApplicationFailure.IdentifierSchemeCodeConflict();
            case "version" -> new ApplicationFailure.IdentifierSchemeVersionMismatch();
            case "locked" -> new ApplicationFailure.IdentifierSchemeRulesLocked();
            case "retired" -> new ApplicationFailure.IdentifierSchemeRetired();
            case "lifecycle" -> new ApplicationFailure.InvalidIdentifierSchemeLifecycle();
            case "exhausted" -> new ApplicationFailure.IdentifierSchemeVersionExhausted();
            case "range" -> new ApplicationFailure.IdentifierSchemeLengthRangeInvalid();
            case "configuration" -> new ApplicationFailure.InvalidIdentifierSchemeConfiguration();
            case "cursor" -> new ApplicationFailure.InvalidIdentifierSchemeCursor();
            case "key" -> new ApplicationFailure.IdempotencyKeyConflict("private key");
            case "dependency" -> new ApplicationFailure.DependencyUnavailable("private host");
            case "corruption" -> new ApplicationFailure.PersistenceFailure();
            default -> throw new IllegalArgumentException("Unknown test scenario");
        };
        return new ApplicationException(failure, new IllegalStateException("private nested cause"));
    }

    private static IdentifierScheme scheme(String scenario) {
        IdentifierSchemeStatus status = switch (scenario) {
            case "locked" -> IdentifierSchemeStatus.ACTIVE;
            case "retired", "stale" -> IdentifierSchemeStatus.RETIRED;
            default -> IdentifierSchemeStatus.DRAFT;
        };
        long version = scenario.equals("exhausted") ? Long.MAX_VALUE : 1;
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString(ID)), "Exact", "EC", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, "name", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 1, 20, false,
                status, new IdentifierSchemeVersion(version), AuditInfo.initial(Instant.parse("2026-09-30T00:00:00Z"), "creator"));
    }
}
