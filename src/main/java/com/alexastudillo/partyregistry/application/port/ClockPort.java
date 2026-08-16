package com.alexastudillo.partyregistry.application.port;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
