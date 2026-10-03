package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.CreateIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Constructs scheme commands with ordered transport validation and shared operation-header rules. */
@ApplicationScoped
public class IdentifierSchemeRequestSupport {
    private final ApiRequestSupport headers;
    private final IdentifierSchemeRequestParameters parameters;

    @Inject
    public IdentifierSchemeRequestSupport(ApiRequestSupport headers, IdentifierSchemeRequestParameters parameters) {
        this.headers = Objects.requireNonNull(headers, "headers");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    /** Rejects noncanonical selectors without trimming or accepting shortened UUID groups. */
    public IdentifierSchemeId schemeId(@Nullable String value) {
        if (value != null) {
            try {
                UUID parsed = UUID.fromString(value);
                if (parsed.toString().equals(value)) {
                    return new IdentifierSchemeId(parsed);
                }
            } catch (IllegalArgumentException _) {
                // The public failure never includes the rejected selector.
            }
        }
        throw new ApiResponseException(PartyResponseCode.IDENTIFIER_SCHEME_ID_INVALID);
    }

    /** Reuses the canonical bare-decimal shared header parser without imposing Party semantics. */
    public IdentifierSchemeVersion expectedVersion(HttpHeaders requestHeaders) {
        return new IdentifierSchemeVersion(headers.requireExpectedVersion(requestHeaders).value());
    }

    /** Requires valid structural input before the mandatory creation key. */
    public CreateIdentifierSchemeCommand createCommand(RequestMetadata metadata,
            @Nullable IdentifierSchemeCreateRequest request, HttpHeaders requestHeaders) {
        var input = parameters.create(request);
        return new CreateIdentifierSchemeCommand(metadata, headers.requireIdempotencyKey(requestHeaders), input);
    }

    /** Validates body before ID before expected version, preserving arbitrary bounds and presence. */
    public PatchIdentifierSchemeCommand patchCommand(RequestMetadata metadata,
            @Nullable IdentifierSchemePatchRequest request, @Nullable String id, HttpHeaders requestHeaders) {
        var changes = parameters.patch(request);
        var parsedId = schemeId(id);
        return new PatchIdentifierSchemeCommand(metadata, parsedId, expectedVersion(requestHeaders), changes);
    }

    /** Validates an optional exact lifecycle key before ID and expected version, including replay input. */
    public ChangeIdentifierSchemeLifecycleCommand lifecycleCommand(RequestMetadata metadata, @Nullable String id,
            IdentifierSchemeLifecycleAction action, HttpHeaders requestHeaders) {
        var key = headers.optionalIdempotencyKey(requestHeaders);
        var parsedId = schemeId(id);
        return new ChangeIdentifierSchemeLifecycleCommand(metadata, parsedId, expectedVersion(requestHeaders), action, key);
    }

    /** Checks framework-buffered bytes before optional key, ID, and version; performs no stream I/O. */
    public ChangeIdentifierSchemeLifecycleCommand lifecycleCommand(RequestMetadata metadata, byte @Nullable [] body,
            @Nullable String id, IdentifierSchemeLifecycleAction action, HttpHeaders requestHeaders) {
        requireNoBody(body);
        return lifecycleCommand(metadata, id, action, requestHeaders);
    }

    /** Accepts absent or zero-byte framework bodies and rejects every supplied byte, including whitespace. */
    public void requireNoBody(byte @Nullable [] body) {
        if (body != null && body.length != 0) {
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }
}
