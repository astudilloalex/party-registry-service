package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import io.smallrye.mutiny.Uni;

import java.util.function.Supplier;

/** Owns atomic Party-scoped nationality writes and optional historical replay. */
public interface NationalityMutationPort {

    /** Resolves replay and Party ownership before invoking the lazy country-validation callback. */
    Uni<NationalityMutationOutcome> create(CreateNationalityCommand command, Supplier<Uni<Void>> validateCountry);

    /** Merges only supplied bounds under the tenant-qualified Party root lock. */
    Uni<NationalityMutationOutcome> patch(PatchNationalityCommand command);

    /** Transfers only intersecting primary designations, optionally replaying a completed action. */
    Uni<NationalityMutationOutcome> setPrimary(SetPrimaryNationalityCommand command);

}
