package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetNationalityQuery;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Distinguishes a concealed/absent Party from an absent nationality under that Party. */
public final class GetNationalityUseCase {

    private final NationalityReadPort reads;

    public GetNationalityUseCase(NationalityReadPort reads) {
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    /** Returns detached historical or future detail without contacting the geographic reference. */
    public Uni<NationalityResult> execute(GetNationalityQuery query) {
        Objects.requireNonNull(query, "query");
        return reads.partyExists(query.tenantId(), query.partyId()).flatMap(owned -> {
            if (!Boolean.TRUE.equals(owned)) {
                return Uni.createFrom().failure(new ApplicationException(
                        new ApplicationFailure.PartyNotFound(query.partyId(), query.tenantId())));
            }
            return reads.findById(query.tenantId(), query.partyId(), query.nationalityId())
                    .flatMap(result -> result.map(Uni.createFrom()::item)
                            .orElseGet(() -> Uni.createFrom().failure(new ApplicationException(
                                    new ApplicationFailure.NationalityNotFound()))));
        });
    }
}
