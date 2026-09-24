package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonDetailResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierReadPort;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Retrieves tenant-scoped natural persons without revealing concealed parties.
 */
public final class GetNaturalPersonUseCase {

    private final NaturalPersonRepository repository;
    private final PartyIdentifierReadPort identifierReadPort;
    private final Clock clock;

    public GetNaturalPersonUseCase(
            NaturalPersonRepository repository, PartyIdentifierReadPort identifierReadPort, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.identifierReadPort = Objects.requireNonNull(identifierReadPort, "identifierReadPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Retrieves a natural person belonging to the requesting tenant.
     *
     * @param command tenant-scoped retrieval command
     * @return the matching person and current masked identifiers evaluated against one UTC date
     */
    public Uni<NaturalPersonDetailResult> execute(GetNaturalPersonCommand command) {
        Objects.requireNonNull(command, "command");

        return repository.findByTenantAndId(command.tenantId(), command.partyId())
                .onItem().transformToUni(found -> found
                        .map(person -> identifierReadPort.findCurrentByParty(
                                        command.tenantId(), command.partyId(),
                                        LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
                                .map(identifiers -> new NaturalPersonDetailResult(
                                        NaturalPersonResult.fromAggregate(person), identifiers)))
                        .orElseGet(() -> Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.NaturalPersonNotFound(
                                        command.partyId(),
                                        command.tenantId())))));
    }
}
