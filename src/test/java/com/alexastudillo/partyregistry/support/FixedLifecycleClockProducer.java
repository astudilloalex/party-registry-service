package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyLifecycleUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;

/** Supplies a fixed non-UTC clock only when explicitly selected by a lifecycle contract test profile. */
@Alternative
@ApplicationScoped
public class FixedLifecycleClockProducer {

    public static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(23, 30, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.ofHours(14));

    /** Exercises the real workflow and persistence while making UTC expiration boundaries deterministic. */
    @Produces
    ChangePartyLifecycleUseCase lifecycleUseCase(PartyMutationPort mutations, OperationObservationPort observations) {
        return new ChangePartyLifecycleUseCase(mutations, CLOCK, observations);
    }
}
