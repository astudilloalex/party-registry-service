package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPrimaryRequest;
import com.alexastudillo.partyregistry.application.command.SetPrimaryPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.command.PatchPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.command.ChangePartyIdentifierLifecycleCommand;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.query.GetPartyIdentifierQuery;
import com.alexastudillo.partyregistry.application.query.ListPartyIdentifiersQuery;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Constructs typed identifier inputs with ordered parent, child and query transport validation. */
@ApplicationScoped
public class PartyIdentifierRequestSupport {

    private static final Pattern NONNEGATIVE_DECIMAL = Pattern.compile("0|[1-9]\\d*");

    private final ApiRequestSupport requests;

    @Inject
    public PartyIdentifierRequestSupport(ApiRequestSupport requests) {
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    /** Parses a nested identifier selector without disclosing rejected input. */
    public PartyIdentifierId identifierId(@Nullable String value) {
        if (value != null) {
            try {
                UUID parsed = UUID.fromString(value);
                if (parsed.toString().equals(value)) {
                    return new PartyIdentifierId(parsed);
                }
            } catch (IllegalArgumentException _) {
                // Binding failures carry only the stable public code.
            }
        }
        throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
    }

    /** Checks the canonical Party path before the canonical identifier path. */
    public GetPartyIdentifierQuery detailQuery(RequestMetadata metadata, String party, @Nullable String identifier) {
        var partyId = requests.parsePartyId(party);
        return new GetPartyIdentifierQuery(metadata, partyId, identifierId(identifier));
    }

    /** Checks parent syntax before validating the complete list query multimap. */
    public ListPartyIdentifiersQuery listQuery(RequestMetadata metadata, String party,
            Map<String, List<String>> parameters) {
        var partyId = requests.parsePartyId(party);
        return PartyIdentifierQueryParameters.parse(metadata, partyId, parameters);
    }

    /** Parses an expected identifier version independently of the owning Party's version contract. */
    public PartyIdentifierVersion identifierVersion(HttpHeaders headers) {
        if (headers == null) { throw new ApiResponseException(PartyResponseCode.BAD_REQUEST); }
        List<String> values = headers.getRequestHeader(ApiRequestSupport.IF_MATCH_HEADER);
        if (values == null || values.isEmpty()) { throw new ApiResponseException(PartyResponseCode.IF_MATCH_REQUIRED); }
        if (values.size() != 1) { throw new ApiResponseException(PartyResponseCode.IF_MATCH_DUPLICATED); }
        String value = values.getFirst();
        if (value == null || !NONNEGATIVE_DECIMAL.matcher(value).matches()) { throw new ApiResponseException(PartyResponseCode.IF_MATCH_INVALID); }
        try { return new PartyIdentifierVersion(Long.parseLong(value)); }
        catch (NumberFormatException _) { throw new ApiResponseException(PartyResponseCode.IF_MATCH_OUT_OF_RANGE); }
    }

    /** Maps validated presence-aware metadata after ordered parent, child and version syntax checks; ignores replay headers. */
    public PatchPartyIdentifierCommand patchCommand(RequestMetadata metadata, String party, String identifier,
            PartyIdentifierPatchRequest request, HttpHeaders headers) {
        var body = requests.validateBody(request);
        if (body.empty()) { throw new ApiResponseException(PartyResponseCode.PATCH_PROPERTY_REQUIRED); }
        var partyId = requests.parsePartyId(party);
        var identifierId = identifierId(identifier);
        return new PatchPartyIdentifierCommand(metadata, partyId, identifierId, identifierVersion(headers), body.issuerCode(), body.issuedOn(), body.expiresOn());
    }

    /** Maps body-validated lifecycle input in parent, child, version and optional-key syntax order. */
    public ChangePartyIdentifierLifecycleCommand lifecycleCommand(RequestMetadata metadata, String party, String identifier,
            HttpHeaders headers, PartyIdentifierOperation operation, PartyIdentifierReason reason) {
        var partyId = requests.parsePartyId(party);
        var identifierId = identifierId(identifier);
        var version = identifierVersion(headers);
        return new ChangePartyIdentifierLifecycleCommand(metadata, partyId, identifierId, version, operation,
                requests.optionalIdempotencyKey(headers).orElse(null), reason);
    }

    /** Maps the effective replacement choice after ordered action selector/header validation. */
    public SetPrimaryPartyIdentifierCommand primaryCommand(RequestMetadata metadata, String party, String identifier,
            @Nullable PartyIdentifierPrimaryRequest request, HttpHeaders headers) {
        var partyId = requests.parsePartyId(party);
        var identifierId = identifierId(identifier);
        var version = identifierVersion(headers);
        return new SetPrimaryPartyIdentifierCommand(metadata, partyId, identifierId, version,
                requests.optionalIdempotencyKey(headers).orElse(null), request != null && request.replaceCurrentPrimary());
    }
}
