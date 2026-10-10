package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import io.smallrye.mutiny.Uni;

/** Establishes owned serialized fresh identifier state and version before any mutation-specific Domain decision. */
final class PartyIdentifierMutationScope {
    private PartyIdentifierMutationScope() { }

    static Uni<PartyIdentifier> locked(PartyIdentifierMutationContext context, RequestMetadata metadata, PartyId party,
            PartyIdentifierId identifier, PartyIdentifierVersion expected) {
        return context.findOwnedParty(party).map(found -> found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.PartyNotFound(party, metadata.tenantId()))))
                .chain(_ -> context.findIdentifierSchemeId(party, identifier))
                .map(found -> found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierNotFound())))
                .chain(scheme -> context.serializeScope(party, scheme).chain(() -> context.findForUpdate(party, identifier))
                        .map(found -> requireCurrent(found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.IdentifierNotFound())),
                                scheme, metadata, party, identifier, expected)));
    }

    private static PartyIdentifier requireCurrent(PartyIdentifier current, IdentifierSchemeId scheme, RequestMetadata metadata,
            PartyId party, PartyIdentifierId identifier, PartyIdentifierVersion expected) {
        if (!current.tenantId().equals(metadata.tenantId()) || !current.partyId().equals(party)
                || !current.identifierId().equals(identifier) || !current.identifierSchemeId().equals(scheme)) {
            throw new IllegalStateException("Locked identifier does not match its resolved owned scope");
        }
        if (!current.version().equals(expected)) { throw new ApplicationException(new ApplicationFailure.IdentifierVersionMismatch(expected, current.version())); }
        return current;
    }
}
