package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.infrastructure.persistence.HibernateReactiveIdentifierSearchReadAdapter;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Injects controlled read failures only when explicitly selected by the fault test profile. */
@Alternative
@ApplicationScoped
public class IdentifierSearchFaultReadPort implements IdentifierSearchReadPort {
    @Inject HibernateReactiveIdentifierSearchReadAdapter delegate;
    private volatile Kind kind = Kind.HEALTHY;

    void select(Kind selected) { kind = selected; }

    @Override
    public <T> Uni<T> withRead(TenantId tenant, Function<IdentifierSearchReadContext, Uni<T>> work) {
        return switch (kind) {
            case HEALTHY -> delegate.withRead(tenant, work);
            case DEPENDENCY -> Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.DependencyUnavailable("search-test"),
                    new IllegalStateException("SensitiveSelector SQL protected-value")));
            case UNEXPECTED -> Uni.createFrom().failure(new IllegalStateException("SensitiveSelector protected-value",
                    new IllegalArgumentException("SensitiveCiphertext")));
            case CORRUPTION -> Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.PersistenceFailure(),
                    new IllegalStateException("SensitiveCiphertext")));
            case SYNCHRONOUS -> throw new IllegalStateException("SensitiveSelector synchronous");
            case COUNT_OVERFLOW -> work.apply(new OverflowContext());
        };
    }

    /** Enumerates controlled terminal signals, leaving the real implementation available for subsequent healthy requests. */
    enum Kind { HEALTHY, DEPENDENCY, UNEXPECTED, CORRUPTION, SYNCHRONOUS, COUNT_OVERFLOW }

    /** Drives genuine use-case and API count conversion without allocating an oversized collection. */
    private static final class OverflowContext implements IdentifierSearchReadContext {
        @Override
        public Uni<Optional<IdentifierScheme>> findSchemeByCode(String code) {
            return Uni.createFrom().item(Optional.of(IdentifierScheme.create(new IdentifierSchemeId(UUID.randomUUID()), code, "EC",
                    IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Overflow fixture", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1",
                    1, 256, false, Instant.EPOCH, "test")));
        }

        @Override
        public Uni<ExactIdentifierSearchSlice> findExact(IdentifierSchemeId scheme, IdentifierSearchSelector selector, IdentifierSearchFilters filters) {
            return Uni.createFrom().failure(new ArithmeticException("Search count exceeds response capacity"));
        }

        @Override
        public Uni<PrefixIdentifierSearchSlice> findPrefix(IdentifierSearchScope scope, Optional<PartyIdentifierPageBoundary> boundary) {
            return Uni.createFrom().item(new PrefixIdentifierSearchSlice(List.of(), (long) Integer.MAX_VALUE + 1,
                    Optional.empty(), Optional.empty()));
        }
    }
}
