package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetLegalEntityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.port.LegalEntityRepository;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Retrieves a tenant-qualified legal entity and projects its current, unchanged details. */
public final class GetLegalEntityUseCase {

    private final LegalEntityRepository repository;

    public GetLegalEntityUseCase(LegalEntityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Retrieves legal details without performing writes or identifier/country lookups.
     *
     * @param command trusted tenant and requested Party identity
     * @return current legal details, or a transport-neutral legal-entity absence failure
     */
    public Uni<LegalEntityResult> execute(GetLegalEntityCommand command) {
        Objects.requireNonNull(command, "command");
        return Uni.createFrom().deferred(() -> repository.findByTenantAndId(command.tenantId(), command.partyId()))
                .map(found -> found.orElseThrow(() -> new ApplicationException(
                        new ApplicationFailure.LegalEntityNotFound(command.partyId(), command.tenantId()))))
                .map(LegalEntityResult::fromAggregate);
    }
}
