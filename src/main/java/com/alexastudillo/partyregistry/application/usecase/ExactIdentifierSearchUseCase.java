package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.IdentifierRuleCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class ExactIdentifierSearchUseCase {
    private final IdentifierSchemeCatalogPort schemes;
    private final IdentifierRuleCatalogPort rules;
    private final IdentifierProtectionPort protection;
    private final IdentifierQueryPort identifiers;

    public ExactIdentifierSearchUseCase(
            IdentifierSchemeCatalogPort schemes,
            IdentifierRuleCatalogPort rules,
            IdentifierProtectionPort protection,
            IdentifierQueryPort identifiers) {
        this.schemes = Objects.requireNonNull(schemes, "schemes");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    }

    public CompletionStage<List<PartyIdentifierResult>> execute(
            RequestContext context, UUID schemeId, String plaintext) {
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(schemeId, "schemeId");
        UseCaseSupport.nonblank(plaintext, "identifier");
        return schemes.findUsableById(schemeId)
                .thenApply(scheme -> UseCaseSupport.found(scheme, "Identifier Scheme"))
                .thenApply(scheme -> rules.normalizeAndValidate(scheme, plaintext))
                .thenCompose(normalized -> protection.fingerprint(context.tenantId(), normalized))
                .thenCompose(hash -> identifiers.findExact(context.tenantId(), schemeId, hash))
                .thenApply(matches -> matches.stream().map(PartyIdentifierResult::from).toList());
    }
}
