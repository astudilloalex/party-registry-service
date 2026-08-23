package com.alexastudillo.partyregistry.application.party.port.out;

import io.smallrye.mutiny.Uni;
import java.time.Instant;

public interface TimeProvider {

    Uni<Instant> now();
}
