package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/** Captures one UTC evaluation date and coordinates an optional keyed primary transfer. */
public final class SetPrimaryNationalityUseCase {

    private final NationalityMutationPort mutations;
    private final Clock clock;

    public SetPrimaryNationalityUseCase(NationalityMutationPort mutations, Clock clock) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Makes one eligibility decision for each subscription without using request actor/correlation for replay identity. */
    public Uni<NationalityMutationOutcome> execute(RequestMetadata metadata, PartyId partyId,
            NationalityId nationalityId, Optional<String> key) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(nationalityId, "nationalityId");
        Objects.requireNonNull(key, "key");
        return Uni.createFrom().deferred(() -> {
            LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            return mutations.setPrimary(new SetPrimaryNationalityCommand(metadata, partyId, nationalityId, date, key));
        });
    }
}
