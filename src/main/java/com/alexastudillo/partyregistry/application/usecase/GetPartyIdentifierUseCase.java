package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.query.GetPartyIdentifierQuery;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Retrieves safe historical identifier detail while concealing absent tenant-qualified ownership. */
public final class GetPartyIdentifierUseCase {

    private final PartyLookupPort owners;
    private final PartyIdentifierQueryPort identifiers;

    public GetPartyIdentifierUseCase(PartyLookupPort owners, PartyIdentifierQueryPort identifiers) {
        this.owners = Objects.requireNonNull(owners, "owners");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    }

    /** Returns safe stored evidence in any state; ownership and absence failures remain transport-neutral. */
    public Uni<PartyIdentifierResult> execute(GetPartyIdentifierQuery query) {
        Objects.requireNonNull(query, "query");
        return Uni.createFrom().deferred(() -> owners.findType(query.requestMetadata().tenantId(), query.partyId())
                .flatMap(owner -> {
                    if (owner.isEmpty()) {
                        return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.PartyNotFound(
                                query.partyId(), query.requestMetadata().tenantId())));
                    }
                    return identifiers.findById(query.requestMetadata(), query.partyId(), query.identifierId())
                            .map(identifier -> identifier.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierNotFound())));
                }));
    }
}
