package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.assertFailureCode;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.construct;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.sequencedPort;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.useCase;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.value;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class IdentifierOrchestrationContractTest {
    private static final UUID TENANT = UUID.fromString("028f0c72-4a7b-7c91-8b2a-1234567890ac");
    private static final UUID PROCESS = UUID.fromString("028f0c72-4a7b-7c91-8b2a-1234567890ae");
    private static final UUID PARTY = UUID.fromString("028f0c72-4a7b-7c91-8b2a-1234567890ab");
    private static final UUID SCHEME = UUID.fromString("028f0c72-4a7b-7c91-8b2a-1234567890af");
    private static final UUID IDENTIFIER = UUID.fromString("028f0c72-4a7b-7c91-8b2a-1234567890b0");
    private static final String SYNTHETIC_PLAINTEXT = "SYNTHETIC-12345678";
    private static final String HASH = "A".repeat(64);

    @Test
    void identifierCreationReadsSchemeThenNormalizesProtectsAndCommitsOneAtomicOutboxIntent() {
        List<String> sequence = new ArrayList<>();
        var catalog = sequencedPort("IdentifierSchemeCatalogPort", sequence)
                .returns("findUsableById", CompletableFuture.completedFuture(scheme()));
        var rules = sequencedPort("IdentifierRuleCatalogPort", sequence)
                .returns("normalizeAndValidate", normalized());
        var protection = sequencedPort("IdentifierProtectionPort", sequence)
                .returns("protect", CompletableFuture.completedFuture(protectedValue()));
        var unitOfWork = sequencedPort("IdentifierUnitOfWorkPort", sequence)
                .answers("createIdentifierAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, invoke(intent, "tenantId"));
                    assertEquals(SCHEME, invoke(intent, "schemeId"));
                    assertEquals(HASH, invoke(invoke(intent, "protectedIdentifier"), "normalizedValueHash"));
                    assertEquals("****5678", invoke(invoke(intent, "protectedIdentifier"), "maskedValue"));
                    assertEquals("party.identifier-created.v1", invoke(invoke(intent, "outbox"), "eventType"));
                    return CompletableFuture.completedFuture(value("MutationResult", IDENTIFIER, 0L,
                            UUID.fromString("028f0c72-4a7b-7c91-8b2a-1234567890b1"),
                            Instant.parse("2026-08-16T00:00:00Z")));
                });
        Object service = useCase("CreatePartyIdentifierUseCase",
                catalog.proxy(), rules.proxy(), protection.proxy(), unitOfWork.proxy());

        Object result = invoke(service, "execute", context(), PARTY, SCHEME, SYNTHETIC_PLAINTEXT);

        assertEquals(List.of(
                "IdentifierSchemeCatalogPort.findUsableById",
                "IdentifierRuleCatalogPort.normalizeAndValidate",
                "IdentifierProtectionPort.protect",
                "IdentifierUnitOfWorkPort.createIdentifierAndAppendOutbox"), sequence);
        assertEquals(IDENTIFIER, invoke(result, "resourceId"));
    }

    @Test
    void unsupportedSchemeRuleFailsWithoutProtectionMutationOrOutbox() {
        List<String> sequence = new ArrayList<>();
        var catalog = sequencedPort("IdentifierSchemeCatalogPort", sequence)
                .returns("findUsableById", CompletableFuture.completedFuture(scheme()));
        var rules = sequencedPort("IdentifierRuleCatalogPort", sequence)
                .answers("normalizeAndValidate", ignored -> {
                    throw (RuntimeException) construct(
                            APPLICATION + "ApplicationFailure", "VALIDATION_ERROR", "unsupported rule key");
                });
        var protection = sequencedPort("IdentifierProtectionPort", sequence)
                .returns("protect", CompletableFuture.completedFuture(protectedValue()));
        var unitOfWork = sequencedPort("IdentifierUnitOfWorkPort", sequence)
                .returns("createIdentifierAndAppendOutbox", CompletableFuture.completedFuture(null));
        Object service = useCase("CreatePartyIdentifierUseCase",
                catalog.proxy(), rules.proxy(), protection.proxy(), unitOfWork.proxy());

        assertFailureCode("VALIDATION_ERROR", () ->
                invoke(service, "execute", context(), PARTY, SCHEME, SYNTHETIC_PLAINTEXT));

        assertEquals(List.of(
                "IdentifierSchemeCatalogPort.findUsableById",
                "IdentifierRuleCatalogPort.normalizeAndValidate"), sequence);
        assertEquals(0, protection.count("protect"));
        assertEquals(0, unitOfWork.count("createIdentifierAndAppendOutbox"));
    }

    @Test
    void exactLookupNormalizesAndFingerprintsForTenantWithoutDecryptingOrScanning() {
        List<String> sequence = new ArrayList<>();
        var catalog = sequencedPort("IdentifierSchemeCatalogPort", sequence)
                .returns("findUsableById", CompletableFuture.completedFuture(scheme()));
        var rules = sequencedPort("IdentifierRuleCatalogPort", sequence)
                .returns("normalizeAndValidate", normalized());
        var protection = sequencedPort("IdentifierProtectionPort", sequence)
                .returns("fingerprint", CompletableFuture.completedFuture(HASH))
                .returns("decrypt", CompletableFuture.completedFuture(SYNTHETIC_PLAINTEXT));
        var query = sequencedPort("IdentifierQueryPort", sequence)
                .answers("findExact", arguments -> {
                    assertEquals(TENANT, arguments[0]);
                    assertEquals(SCHEME, arguments[1]);
                    assertEquals(HASH, arguments[2]);
                    return CompletableFuture.completedFuture(List.of(identifierView()));
                });
        Object service = useCase("ExactIdentifierSearchUseCase",
                catalog.proxy(), rules.proxy(), protection.proxy(), query.proxy());

        Object result = invoke(service, "execute", context(), SCHEME, SYNTHETIC_PLAINTEXT);

        assertEquals(List.of(
                "IdentifierSchemeCatalogPort.findUsableById",
                "IdentifierRuleCatalogPort.normalizeAndValidate",
                "IdentifierProtectionPort.fingerprint",
                "IdentifierQueryPort.findExact"), sequence);
        assertEquals(0, protection.count("decrypt"));
        assertEquals("****5678", invoke(((List<?>) result).getFirst(), "maskedValue"));
    }

    @Test
    void decryptionLogsRequiredContextAfterDecryptAndBeforePlaintextDisclosure() {
        List<String> sequence = new ArrayList<>();
        var query = sequencedPort("IdentifierQueryPort", sequence)
                .returns("findProtectedByTenantAndId", CompletableFuture.completedFuture(identifierView()));
        var protection = sequencedPort("IdentifierProtectionPort", sequence)
                .returns("decrypt", CompletableFuture.completedFuture(SYNTHETIC_PLAINTEXT));
        var securityLog = sequencedPort("DecryptionSecurityLogPort", sequence)
                .answers("emit", arguments -> {
                    Object event = arguments[0];
                    assertEquals(TENANT, invoke(event, "tenantId"));
                    assertEquals("synthetic-service", invoke(event, "userId"));
                    assertEquals(PROCESS, invoke(event, "processId"));
                    assertEquals(IDENTIFIER, invoke(event, "partyIdentifierId"));
                    assertEquals("SUCCESS", invoke(event, "outcome"));
                    return CompletableFuture.completedFuture(null);
                });
        Object service = useCase("DecryptPartyIdentifierUseCase", query.proxy(), protection.proxy(), securityLog.proxy());

        Object result = invoke(service, "execute", context(), IDENTIFIER);

        assertEquals(List.of(
                "IdentifierQueryPort.findProtectedByTenantAndId",
                "IdentifierProtectionPort.decrypt",
                "DecryptionSecurityLogPort.emit"), sequence);
        assertEquals(SYNTHETIC_PLAINTEXT, invoke(result, "plaintext"));
        assertEquals(true, invoke(result, "noStore"));
    }

    @Test
    void synchronousSecurityLogFailureWithholdsPlaintextAndLeavesIdentifierUnchanged() {
        List<String> sequence = new ArrayList<>();
        var query = sequencedPort("IdentifierQueryPort", sequence)
                .returns("findProtectedByTenantAndId", CompletableFuture.completedFuture(identifierView()));
        var protection = sequencedPort("IdentifierProtectionPort", sequence)
                .returns("decrypt", CompletableFuture.completedFuture(SYNTHETIC_PLAINTEXT));
        var securityLog = sequencedPort("DecryptionSecurityLogPort", sequence)
                .returns("emit", CompletableFuture.failedFuture(
                        (Throwable) construct(
                                APPLICATION + "ApplicationFailure", "INTERNAL_ERROR", "security log unavailable")));
        Object service = useCase("DecryptPartyIdentifierUseCase", query.proxy(), protection.proxy(), securityLog.proxy());

        assertFailureCode("INTERNAL_ERROR", () -> invoke(service, "execute", context(), IDENTIFIER));

        assertEquals(List.of(
                "IdentifierQueryPort.findProtectedByTenantAndId",
                "IdentifierProtectionPort.decrypt",
                "DecryptionSecurityLogPort.emit"), sequence);
        assertEquals(0, query.count("save"));
    }

    private static Object context() {
        return construct(APPLICATION + "RequestContext", TENANT.toString(), "synthetic-service", PROCESS.toString(),
                (Supplier<UUID>) () -> PROCESS);
    }

    private static Object scheme() {
        return value("IdentifierSchemeMetadata", SCHEME, "EC_SYNTHETIC", "ACTIVE", "NATURAL_PERSON",
                "trim-v1", "synthetic-v1", 5, 32, false);
    }

    private static Object normalized() {
        return value("NormalizedIdentifier", "SYNTHETIC12345678", 1);
    }

    private static Object protectedValue() {
        return value("ProtectedIdentifierData", "synthetic-ciphertext", HASH, "****5678", 1);
    }

    private static Object identifierView() {
        return value("PartyIdentifierView", IDENTIFIER, TENANT, PARTY, SCHEME,
                "synthetic-ciphertext", "****5678", 1, 3L);
    }
}
