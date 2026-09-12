package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Resolves official identifier schemes by stable catalog code.
 */
public interface IdentifierSchemeRepository {

    /**
     * Finds a scheme without interpreting its lifecycle or Party compatibility.
     *
     * @param code stable scheme code
     * @return the matching scheme, or empty when the code is unknown
     */
    Uni<Optional<IdentifierScheme>> findByCode(String code);
}
