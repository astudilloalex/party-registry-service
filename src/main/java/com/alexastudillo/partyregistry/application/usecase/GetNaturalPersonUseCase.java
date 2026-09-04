package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/**
 * Retrieves tenant-scoped natural persons without revealing concealed parties.
 */
public final class GetNaturalPersonUseCase {

    private final NaturalPersonRepository repository;

    public GetNaturalPersonUseCase(NaturalPersonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Retrieves a natural person belonging to the requesting tenant.
     *
     * @param command tenant-scoped retrieval command
     * @return the matching application result
     */
    public Uni<NaturalPersonResult> execute(GetNaturalPersonCommand command) {
        Objects.requireNonNull(command, "command");

        return repository.findByTenantAndId(command.tenantId(), command.partyId())
                .onItem().transformToUni(found -> found
                        .map(person -> Uni.createFrom().item(NaturalPersonResult.fromAggregate(person)))
                        .orElseGet(() -> Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.NaturalPersonNotFound(
                                        command.partyId(),
                                        command.tenantId())))));
    }
}
