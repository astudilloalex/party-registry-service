package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Coordinates one atomic, presence-aware nationality validity correction. */
public final class PatchNationalityUseCase {

    private final NationalityMutationPort mutations;

    public PatchNationalityUseCase(NationalityMutationPort mutations) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
    }

    public Uni<NationalityMutationOutcome> execute(PatchNationalityCommand command) {
        return mutations.patch(Objects.requireNonNull(command, "command"));
    }
}
