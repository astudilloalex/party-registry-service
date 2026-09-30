package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.UUID;

/** Provides test-source-only failures for exercising root error translation through the real shared global mapper. */
@Path("/v1/root-error-verification")
@Produces(MediaType.APPLICATION_JSON)
public class RootErrorVerificationResource {

    private static final PartyId PARTY_ID = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT_ID = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));

    @Inject
    PartyApiErrorTranslator translator;

    /** Emits only controlled fixture failures; no production route or failure hook is added. */
    @GET
    @Path("/{scenario}")
    public Uni<RestResponse<ApiResponse<Void>>> failure(@PathParam("scenario") String scenario) {
        return Uni.createFrom().<RestResponse<ApiResponse<Void>>>failure(() -> failureFor(scenario))
                .onFailure().transform(translator::translate);
    }

    private static RuntimeException failureFor(String scenario) {
        return switch (scenario) {
            case "required" -> new ApiResponseException(PartyResponseCode.DISPLAY_NAME_REQUIRED);
            case "blank" -> new ApplicationException(new ApplicationFailure.InvalidBusinessState(DomainViolation.DISPLAY_NAME_REQUIRED));
            case "too-long" -> new ApplicationException(new ApplicationFailure.InvalidBusinessState(DomainViolation.DISPLAY_NAME_TOO_LONG));
            case "cursor" -> new ApplicationException(new ApplicationFailure.InvalidPartyCursor());
            case "missing" -> new ApplicationException(new ApplicationFailure.PartyNotFound(PARTY_ID, TENANT_ID));
            case "patch-version" -> new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(PartyVersion.initial(), new PartyVersion(1)));
            case "lifecycle-version" -> new ApplicationException(new ApplicationFailure.StalePartyVersion(PartyVersion.initial(), new PartyVersion(1)));
            case "key-conflict" -> new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict("private-test-key"));
            case "lifecycle" -> new ApplicationException(new ApplicationFailure.InvalidPartyLifecycle(PARTY_ID, PartyRecordStatus.ARCHIVED));
            case "evidence" -> new ApplicationException(new ApplicationFailure.MissingQualifyingIdentifier(PARTY_ID));
            case "unexpected" -> new IllegalStateException("private-internal-detail");
            default -> new IllegalArgumentException("Unknown test scenario");
        };
    }
}
