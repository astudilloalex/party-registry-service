package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Proves CDI resolves the scheme cursor port against the existing eagerly configured signing ring. */
@QuarkusTest
class HmacIdentifierSchemeCursorCompositionTest {
    @Inject
    IdentifierSchemeCursorPort cursors;

    @Inject
    PartyCursorKeyMaterial keys;

    @Test
    void signsAndVerifiesUsingTheConfiguredCurrentKeyWithoutAProducer() {
        var scope = new IdentifierSchemeSearchScope(new TenantId(UUID.randomUUID()),
                new IdentifierSchemeSearchCriteria(null, null, null, null, 50));
        var boundary = new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.PREVIOUS,
                new IdentifierSchemePagePosition(Instant.parse("2026-10-01T10:00:00.123456Z"),
                        new IdentifierSchemeId(UUID.randomUUID())));
        String token = cursors.encode(boundary, scope);
        assertEquals(keys.currentKeyId(), token.split("\\.")[1]);
        assertEquals(boundary, cursors.decode(token, scope));
    }
}
