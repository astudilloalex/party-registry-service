package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.DecryptedIdentifierResult;
import com.alexastudillo.partyregistry.application.DecryptionSecurityLogEvent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.ClockPort;
import com.alexastudillo.partyregistry.application.port.DecryptionSecurityLogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class DecryptPartyIdentifierUseCase {
    private final IdentifierQueryPort identifiers;
    private final IdentifierProtectionPort protection;
    private final DecryptionSecurityLogPort securityLog;
    private final ClockPort clock;

    public DecryptPartyIdentifierUseCase(
            IdentifierQueryPort identifiers,
            IdentifierProtectionPort protection,
            DecryptionSecurityLogPort securityLog) {
        this(identifiers, protection, securityLog, Instant::now);
    }

    public DecryptPartyIdentifierUseCase(
            IdentifierQueryPort identifiers,
            IdentifierProtectionPort protection,
            DecryptionSecurityLogPort securityLog,
            ClockPort clock) {
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.securityLog = Objects.requireNonNull(securityLog, "securityLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<DecryptedIdentifierResult> execute(RequestContext context, UUID identifierId) {
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(identifierId, "identifierId");
        return identifiers.findProtectedByTenantAndId(context.tenantId(), identifierId)
                .thenApply(identifier -> UseCaseSupport.found(identifier, "Party Identifier"))
                .thenCompose(identifier -> protection
                        .decrypt(context.tenantId(), identifier.ciphertext(), identifier.encryptionKeyVersion())
                        .thenCompose(plaintext -> securityLog
                                .emit(new DecryptionSecurityLogEvent(
                                        context.tenantId(),
                                        context.userId(),
                                        context.processId(),
                                        identifierId,
                                        clock.now(),
                                        "PARTY_IDENTIFIER_DECRYPT",
                                        "SUCCESS"))
                                .thenApply(ignored -> new DecryptedIdentifierResult(plaintext, true))));
    }
}
