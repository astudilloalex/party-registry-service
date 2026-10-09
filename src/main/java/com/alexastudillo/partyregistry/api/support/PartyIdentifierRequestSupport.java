package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.query.GetPartyIdentifierQuery;
import com.alexastudillo.partyregistry.application.query.ListPartyIdentifiersQuery;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Constructs typed identifier inputs with ordered parent, child and query transport validation. */
@ApplicationScoped
public class PartyIdentifierRequestSupport {

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
}
