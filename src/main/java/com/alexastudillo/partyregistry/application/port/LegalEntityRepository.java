package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Provides tenant-qualified legal-entity retrieval and atomic version-guarded updates. */
public interface LegalEntityRepository {

    /**
     * Retrieves a detached legal entity without exposing another tenant or Party type.
     *
     * @param tenantId requesting tenant
     * @param partyId requested Party identity
     * @return the legal entity, or empty for absent, concealed, and wrong-type Parties
     */
    Uni<Optional<LegalEntity>> findByTenantAndId(TenantId tenantId, PartyId partyId);

    /**
     * Atomically stores root/detail changes and increments the Party version exactly once.
     *
     * @param candidate updated Domain values retaining the current candidate version
     * @param expectedVersion version that must still match at acceptance
     * @return the accepted aggregate with its persisted version
     * @throws com.alexastudillo.partyregistry.application.error.ApplicationException
     *         with a transport-neutral absence, version mismatch, or persistence failure
     */
    Uni<LegalEntity> update(LegalEntity candidate, PartyVersion expectedVersion);
}
