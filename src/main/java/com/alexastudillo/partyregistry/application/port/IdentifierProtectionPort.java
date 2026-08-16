package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.NormalizedIdentifier;
import com.alexastudillo.partyregistry.application.ProtectedIdentifierData;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface IdentifierProtectionPort {
    CompletionStage<ProtectedIdentifierData> protect(UUID tenantId, NormalizedIdentifier normalizedIdentifier);

    CompletionStage<String> fingerprint(UUID tenantId, NormalizedIdentifier normalizedIdentifier);

    CompletionStage<String> decrypt(UUID tenantId, String ciphertext, int encryptionKeyVersion);
}
