package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Optional;
import java.util.function.Function;

/** Injects only port failures in a selected test profile while retaining the real producers, use cases, resource, and global mapper. */
@Alternative
@ApplicationScoped
public class IdentifierSchemeBoundaryFaultPort implements IdentifierSchemeReadPort, IdentifierSchemeMutationPort {
    private volatile Kind kind = Kind.DEPENDENCY;

    void select(Kind value) {
        kind = value;
    }

    @Override
    public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
        return fail();
    }

    @Override
    public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) {
        return fail();
    }

    @Override
    public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria, Optional<IdentifierSchemePageBoundary> boundary) {
        return fail();
    }

    @Override
    public Uni<IdentifierSchemeMutationOutcome> execute(RequestMetadata metadata,
            Function<IdentifierSchemeMutationContext, Uni<IdentifierSchemeMutationOutcome>> work) {
        return fail();
    }

    private <T> Uni<T> fail() {
        RuntimeException failure = switch (kind) {
            case DEPENDENCY -> new ApplicationException(new ApplicationFailure.DependencyUnavailable("private-database"),
                    new IllegalStateException("private SQL credentials"));
            case UNEXPECTED -> new IllegalStateException("private SQL password stack trace");
            case CORRUPTION -> new ApplicationException(new ApplicationFailure.PersistenceFailure(), new IllegalStateException("private corrupt snapshot"));
            case SYNCHRONOUS -> new IllegalArgumentException("private synchronous failure");
        };
        if (kind == Kind.SYNCHRONOUS) throw failure;
        return Uni.createFrom().failure(failure);
    }

    /** Separates classified outages from unexpected asynchronous, corruption, and synchronous failures. */
    enum Kind {
        DEPENDENCY, UNEXPECTED, CORRUPTION, SYNCHRONOUS
    }
}
