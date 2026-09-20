package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.command.PartyRegistrationCommand;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies capability-oriented application port signatures and dependency isolation.
 */
class ApplicationPortContractTest {

    private static final Set<Class<?>> PORTS = Set.of(
            RegistrationFingerprintPort.class,
            IdentifierSchemeRepository.class,
            IdentifierProtectionPort.class,
            IdempotentPartyRegistrationPort.class,
            PartyLookupPort.class,
            PartyIdentifierRegistrationPort.class,
            PartyMutationPort.class,
            PartyMutationContext.class,
            PartyQueryPort.class,
            PartyCursorPort.class,
            LegalEntityRepository.class);

    @Test
    void exposesQualifiedLegalReadsAndAtomicVersionGuardedUpdates() throws NoSuchMethodException {
        Method lookup = LegalEntityRepository.class.getMethod("findByTenantAndId", TenantId.class, PartyId.class);
        Method update = LegalEntityRepository.class.getMethod("update", LegalEntity.class, PartyVersion.class);
        assertEquals(Uni.class, lookup.getReturnType());
        assertTrue(lookup.getGenericReturnType().getTypeName().contains("java.util.Optional<" + LegalEntity.class.getName()));
        assertEquals(Uni.class, update.getReturnType());
        assertTrue(update.getGenericReturnType().getTypeName().contains(LegalEntity.class.getName()));
    }

    @Test
    void exposesRequiredRegistrationAndLifecycleCapabilities() throws NoSuchMethodException {
        assertEquals(String.class,
                RegistrationFingerprintPort.class
                        .getMethod("fingerprint", PartyRegistrationCommand.class)
                        .getReturnType());
        assertEquals(boolean.class,
                RegistrationFingerprintPort.class
                        .getMethod("matches", String.class, String.class)
                        .getReturnType());
        assertEquals(Uni.class,
                IdentifierSchemeRepository.class.getMethod("findByCode", String.class).getReturnType());
        assertEquals(
                com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue.class,
                IdentifierProtectionPort.class
                        .getMethod("protect", IdentifierProtectionRequest.class)
                        .getReturnType());
        assertEquals(Uni.class,
                IdempotentPartyRegistrationPort.class
                        .getMethod("findCompleted", TenantId.class, String.class, String.class, String.class)
                        .getReturnType());
        assertEquals(Uni.class,
                IdempotentPartyRegistrationPort.class
                        .getMethod("hasCompletedKey", TenantId.class, String.class, String.class)
                        .getReturnType());
        assertEquals(Uni.class,
                IdempotentPartyRegistrationPort.class
                        .getMethod("registerNaturalPerson", NaturalPersonRegistrationCandidate.class)
                        .getReturnType());
        assertEquals(Uni.class,
                IdempotentPartyRegistrationPort.class
                        .getMethod("registerLegalEntity", LegalEntityRegistrationCandidate.class)
                        .getReturnType());
        assertEquals(Uni.class,
                PartyIdentifierRegistrationPort.class
                        .getMethod("register", PartyIdentifierRegistrationCandidate.class)
                        .getReturnType());
        assertEquals(Uni.class,
                PartyMutationPort.class
                        .getMethod("execute", RequestMetadata.class, Function.class)
                        .getReturnType());
    }

    @Test
    void keepsEveryPortFreeOfOuterLayerAndProviderTypes() {
        Set<String> forbiddenFragments = Set.of(
                ".infrastructure.",
                ".api.",
                "org.hibernate",
                "jakarta.persistence",
                "java.sql",
                "javax.crypto",
                "java.security",
                "jakarta.ws.rs",
                "ApiResponse");

        PORTS.forEach(port -> {
            assertTrue(port.isInterface(), port.getName());
            for (Method method : port.getDeclaredMethods()) {
                String signature = method.toGenericString();
                forbiddenFragments.forEach(fragment -> assertFalse(
                        signature.contains(fragment),
                        signature));
            }
        });
    }
}
