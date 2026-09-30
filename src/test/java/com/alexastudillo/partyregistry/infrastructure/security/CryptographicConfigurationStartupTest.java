package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the application test profile supplies eagerly validated cryptographic keys.
 */
@QuarkusTest
@TestProfile(CryptographicConfigurationStartupTest.ConfigurationOnlyProfile.class)
class CryptographicConfigurationStartupTest {

    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));

    @Inject
    IdentifierProtectionPort identifierProtection;

    @Inject
    RegistrationFingerprintPort registrationFingerprint;

    @Test
    void resolvesExistingTestProfilePropertiesWithoutRequestTimeConfigurationLookup() {
        RequestMetadata metadata = new RequestMetadata(
                TENANT_ID,
                "operator",
                UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
        RegisterNaturalPersonCommand command = new RegisterNaturalPersonCommand(
                metadata,
                "idempotency-key",
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null,
                new InitialPartyIdentifierInput("GB-NATIONAL-ID", "AB-1234", null, null, null, false));
        ProtectedIdentifierValue protectedValue = identifierProtection.protect(
                new IdentifierProtectionRequest(
                        TENANT_ID,
                        new PartyId(UUID.fromString("0198ce2c-609c-7c04-a977-425e7d60c58d")),
                        new PartyIdentifierId(UUID.fromString("0198ce2d-7e5a-7d9a-bfe7-42f15172338b")),
                        new IdentifierSchemeId(UUID.fromString("0198ce2e-a08d-7725-b13c-eb72395d4690")),
                        "AB-1234",
                        "AB1234",
                        new IdentifierRuleVersion(1)));

        assertEquals(1, protectedValue.encryptionKeyVersion());
        assertTrue(registrationFingerprint.fingerprint(command).matches("^[0-9a-f]{64}$"));
    }

    /**
     * Disables persistence services unrelated to cryptographic configuration startup.
     */
    public static final class ConfigurationOnlyProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.devservices.enabled", "false",
                    "quarkus.rabbitmq.devservices.enabled", "false",
                    "quarkus.datasource.health.enabled", "false",
                    "quarkus.flyway.migrate-at-start", "false",
                    "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
