package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

/** Exercises the real identifier error translator and published global handler using test-only failed HTTP pipelines. */
@Path("/v1/identifier-error-verification")
@Produces(MediaType.APPLICATION_JSON)
public class PartyIdentifierErrorVerificationResource {

    private final PartyApiErrorTranslator translator;

    @Inject
    public PartyIdentifierErrorVerificationResource(PartyApiErrorTranslator translator) {
        this.translator = translator;
    }

    /** Returns a failed pipeline whose known/unknown failure is processed at the normal HTTP boundary. */
    @GET
    @Path("/{kind}")
    public Uni<RestResponse<ApiResponse<Void>>> fail(@PathParam("kind") String kind) {
        var failure = switch (kind) {
            case "absent" -> new ApplicationException(new ApplicationFailure.IdentifierNotFound());
            case "version" -> new ApplicationException(new ApplicationFailure.IdentifierVersionMismatch(
                    new PartyIdentifierVersion(3), new PartyIdentifierVersion(4)));
            case "lifecycle" -> new ApplicationException(new ApplicationFailure.InvalidIdentifierLifecycle());
            case "primary" -> new ApplicationException(new ApplicationFailure.PrimaryIdentifierConflict());
            case "exhausted" -> new ApplicationException(new ApplicationFailure.IdentifierVersionExhausted());
            case "cursor" -> new ApplicationException(new ApplicationFailure.InvalidIdentifierCursor());
            case "validity" -> ApplicationException.forIdentifier(new DomainValidationException(
                    DomainViolation.IDENTIFIER_EXPIRED, "private-date-detail"));
            default -> ApplicationException.forIdentifier(new DomainValidationException(
                    DomainViolation.PROTECTED_IDENTIFIER_VALUE_REQUIRED, "private-ciphertext-detail"));
        };
        return Uni.createFrom().<RestResponse<ApiResponse<Void>>>failure(failure).onFailure().transform(translator::translate);
    }
}
