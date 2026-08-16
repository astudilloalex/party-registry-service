package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.IdentifierMutationIntent;
import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierRuleCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class CreatePartyIdentifierUseCase {
    private final IdentifierSchemeCatalogPort schemes;
    private final IdentifierRuleCatalogPort rules;
    private final IdentifierProtectionPort protection;
    private final IdentifierUnitOfWorkPort unitOfWork;

    public CreatePartyIdentifierUseCase(
            IdentifierSchemeCatalogPort schemes,
            IdentifierRuleCatalogPort rules,
            IdentifierProtectionPort protection,
            IdentifierUnitOfWorkPort unitOfWork) {
        this.schemes = Objects.requireNonNull(schemes, "schemes");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context, UUID partyId, UUID schemeId, String plaintext) {
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(partyId, "partyId");
        UseCaseSupport.required(schemeId, "schemeId");
        UseCaseSupport.nonblank(plaintext, "identifier");
        return schemes.findUsableById(schemeId)
                .thenApply(scheme -> UseCaseSupport.found(scheme, "Identifier Scheme"))
                .thenApply(scheme -> rules.normalizeAndValidate(scheme, plaintext))
                .thenCompose(normalized -> protection.protect(context.tenantId(), normalized))
                .thenCompose(protectedIdentifier -> unitOfWork.createIdentifierAndAppendOutbox(
                        new IdentifierMutationIntent(
                                context.tenantId(),
                                null,
                                partyId,
                                schemeId,
                                protectedIdentifier,
                                0,
                                null,
                                context.userId(),
                                new OutboxIntent("party.identifier-created.v1", context.processId()))));
    }
}
