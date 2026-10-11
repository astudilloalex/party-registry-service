package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.function.Function;

/** Owns one tenant-qualified consistent logical search read per subscription without exposing its session. */
public interface IdentifierSearchReadPort {
    /** Closes the operation context on completion, failure or cancellation; callers must not retain it. */
    <T> Uni<T> withRead(TenantId tenantId, Function<IdentifierSearchReadContext, Uni<T>> work);
}
