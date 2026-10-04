package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageSlice;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Reads qualified safe historical projections without exposing or loading protected identifier material. */
public interface PartyIdentifierQueryPort {

    /** Conceals absent, wrong-owner and cross-tenant identifiers through one qualified optional result. */
    Uni<Optional<PartyIdentifierResult>> findById(RequestMetadata metadata, PartyId partyId, PartyIdentifierId identifierId);

    /** Returns rows/count/navigation from one consistent read; an owned empty collection is a present empty slice. */
    Uni<PartyIdentifierPageSlice> findPage(RequestMetadata metadata, PartyIdentifierSearchScope scope,
            @Nullable PartyIdentifierPageBoundary boundary);
}
