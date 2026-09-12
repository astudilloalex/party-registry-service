package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies registration inputs, candidates, outbox data, and safe replay
 * results.
 */
class PartyRegistrationContractTest {

        private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
        private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 4);
        private static final TenantId TENANT_ID = new TenantId(
                        UUID.fromString("01991a56-f000-7000-8000-000000000001"));
        private static final PartyId NATURAL_PARTY_ID = new PartyId(
                        UUID.fromString("01991a56-f000-7000-8000-000000000002"));
        private static final PartyId LEGAL_PARTY_ID = new PartyId(
                        UUID.fromString("01991a56-f000-7000-8000-000000000003"));
        private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(
                        UUID.fromString("01991a56-f000-7000-8000-000000000004"));
        private static final String COMPLETE_VALUE = "Sensitive-1234";
        private static final String CIPHERTEXT = "protected-ciphertext";
        private static final String VALUE_HASH = "a".repeat(64);
        private static final String REGISTRATION_FINGERPRINT = "b".repeat(64);

        @Test
        void usesNewDistinctOperationKeysAndRedactsIdentifierInput() {
                InitialPartyIdentifierInput input = identifierInput();
                RegisterNaturalPersonCommand naturalCommand = naturalCommand(input);
                RegisterLegalEntityCommand legalCommand = legalCommand(input);

                assertEquals("REGISTER_NATURAL_PERSON", naturalCommand.operation());
                assertEquals("REGISTER_LEGAL_ENTITY", legalCommand.operation());
                assertEquals("CREATE_NATURAL_PERSON", RegisterNaturalPersonCommand.LEGACY_OPERATION);
                assertNotEquals(naturalCommand.operation(), legalCommand.operation());
                assertNotEquals(RegisterNaturalPersonCommand.LEGACY_OPERATION, naturalCommand.operation());
                assertFalse(input.toString().contains(COMPLETE_VALUE));
                assertTrue(input.toString().contains("value=<redacted>"));
        }

        @Test
        void projectsBothPartyTypesAndOnlySafeIdentifierFields() {
                IdentifierScheme scheme = scheme();
                NaturalPerson naturalPerson = naturalPerson();
                PartyIdentifier identifier = identifier(NATURAL_PARTY_ID);

                PartyDetailsResult naturalResult = PartyDetailsResult.fromAggregate(naturalPerson);
                PartyIdentifierResult identifierResult = PartyIdentifierResult.fromAggregate(identifier, scheme);
                PartyRegistrationResult registrationResult = new PartyRegistrationResult(
                                naturalResult,
                                identifierResult,
                                PartyRegistrationOutcome.REPLAYED);

                assertInstanceOf(NaturalPersonResult.class, naturalResult);
                assertEquals(NATURAL_PARTY_ID, registrationResult.party().partyId());
                assertEquals(identifier.identifierId(), registrationResult.initialIdentifier().identifierId());
                assertEquals("********1234", registrationResult.initialIdentifier().maskedValue());
                assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION,
                                registrationResult.initialIdentifier().status());
                assertEquals(PartyRegistrationOutcome.REPLAYED, registrationResult.outcome());
                assertFalse(registrationResult.toString().contains(CIPHERTEXT));
                assertFalse(registrationResult.toString().contains(VALUE_HASH));

                LegalEntityResult legalResult = assertInstanceOf(
                                LegalEntityResult.class,
                                PartyDetailsResult.fromAggregate(legalEntity()));
                assertEquals(PartyType.LEGAL_ENTITY, legalResult.type());
                assertEquals("Analytical Engines Ltd", legalResult.legalName());
                assertSafeResultComponents(PartyIdentifierResult.class);
                assertSafeResultComponents(PartyRegistrationResult.class);
                assertSafeResultComponents(NaturalPersonResult.class);
                assertSafeResultComponents(LegalEntityResult.class);
        }

        @Test
        void keepsIndependentAggregatesAndDefensivelyCopiesOutboxCandidates() {
                IdentifierScheme scheme = scheme();
                NaturalPerson naturalPerson = naturalPerson();
                PartyIdentifier naturalIdentifier = identifier(NATURAL_PARTY_ID);
                List<OutboxEventCandidate> events = new ArrayList<>();
                events.add(new PartyCreatedOutboxCandidate(
                                UUID.randomUUID(),
                                TENANT_ID,
                                NATURAL_PARTY_ID,
                                naturalPerson.version(),
                                naturalPerson.type(),
                                NOW,
                                UUID.randomUUID(),
                                "operator"));
                events.add(new PartyIdentifierCreatedOutboxCandidate(
                                UUID.randomUUID(),
                                TENANT_ID,
                                naturalIdentifier.identifierId(),
                                naturalIdentifier.version(),
                                NATURAL_PARTY_ID,
                                scheme.code(),
                                naturalIdentifier.status(),
                                NOW,
                                UUID.randomUUID(),
                                "operator"));

                NaturalPersonRegistrationCandidate naturalCandidate = new NaturalPersonRegistrationCandidate(
                                metadata(),
                                "natural-key",
                                REGISTRATION_FINGERPRINT,
                                naturalPerson,
                                scheme,
                                naturalIdentifier,
                                events);
                events.clear();

                assertEquals(RegisterNaturalPersonCommand.OPERATION_NAME, naturalCandidate.operation());
                assertSame(naturalPerson, naturalCandidate.party());
                assertSame(naturalIdentifier, naturalCandidate.initialIdentifier());
                assertEquals(2, naturalCandidate.outboxCandidates().size());

                List<OutboxEventCandidate> additionalEvents = new ArrayList<>(
                                List.of(naturalCandidate.outboxCandidates().get(1)));
                PartyIdentifierRegistrationCandidate additionalCandidate = new PartyIdentifierRegistrationCandidate(
                                naturalCandidate.requestMetadata(),
                                "additional-key",
                                PartyType.NATURAL_PERSON,
                                naturalIdentifier,
                                scheme,
                                additionalEvents);
                additionalEvents.clear();

                assertEquals(PartyType.NATURAL_PERSON, additionalCandidate.partyType());
                assertSame(naturalIdentifier, additionalCandidate.identifier());
                assertEquals(1, additionalCandidate.outboxCandidates().size());

                LegalEntity legalEntity = legalEntity();
                PartyIdentifier legalIdentifier = identifier(LEGAL_PARTY_ID);
                LegalEntityRegistrationCandidate legalCandidate = new LegalEntityRegistrationCandidate(
                                metadata(),
                                "legal-key",
                                REGISTRATION_FINGERPRINT,
                                legalEntity,
                                scheme,
                                legalIdentifier,
                                List.of());

                assertEquals(RegisterLegalEntityCommand.OPERATION_NAME, legalCandidate.operation());
                assertSame(legalEntity, legalCandidate.party());
                assertSame(legalIdentifier, legalCandidate.initialIdentifier());
                assertFalse(Party.class.isAssignableFrom(PartyIdentifier.class));
        }

        @Test
        void redactsTransientProtectionMaterialFromDiagnostics() {
                PartyIdentifierId identifierId = new PartyIdentifierId(UUID.randomUUID());
                IdentifierProtectionRequest request = new IdentifierProtectionRequest(
                                TENANT_ID,
                                NATURAL_PARTY_ID,
                                identifierId,
                                SCHEME_ID,
                                COMPLETE_VALUE,
                                "SENSITIVE1234",
                                new IdentifierRuleVersion(1));

                assertFalse(request.toString().contains(COMPLETE_VALUE));
                assertFalse(request.toString().contains("SENSITIVE1234"));
                assertTrue(request.toString().contains("completeValue=<redacted>"));
                assertTrue(request.toString().contains("normalizedValue=<redacted>"));
        }

        private static void assertSafeResultComponents(Class<?> resultType) {
                Set<String> forbiddenNames = Set.of(
                                "value",
                                "completeValue",
                                "normalizedValue",
                                "encryptedValue",
                                "normalizedValueHash",
                                "encryptionKeyVersion",
                                "normalizationVersion",
                                "registrationFingerprint",
                                "requestFingerprint",
                                "idempotencyKey",
                                "code",
                                "data");
                Set<String> actualNames = Stream.of(resultType.getRecordComponents())
                                .map(RecordComponent::getName)
                                .collect(java.util.stream.Collectors.toSet());
                assertTrue(java.util.Collections.disjoint(forbiddenNames, actualNames), actualNames.toString());
                Stream.of(resultType.getRecordComponents())
                                .map(RecordComponent::getType)
                                .map(Class::getPackageName)
                                .forEach(packageName -> assertFalse(packageName.contains(".api"), packageName));
        }

        private static InitialPartyIdentifierInput identifierInput() {
                return new InitialPartyIdentifierInput(
                                "GB-NATIONAL-ID",
                                COMPLETE_VALUE,
                                "issuer",
                                TODAY.minusYears(1),
                                TODAY.plusYears(5),
                                false);
        }

        private static RegisterNaturalPersonCommand naturalCommand(InitialPartyIdentifierInput input) {
                return new RegisterNaturalPersonCommand(
                                metadata(),
                                "natural-key",
                                null,
                                "Ada",
                                "Lovelace",
                                null,
                                LocalDate.of(1815, Month.DECEMBER, 10),
                                LocalDate.of(1852, Month.NOVEMBER, 27),
                                "GB",
                                input);
        }

        private static RegisterLegalEntityCommand legalCommand(InitialPartyIdentifierInput input) {
                return new RegisterLegalEntityCommand(
                                metadata(),
                                "legal-key",
                                null,
                                "Analytical Engines Ltd",
                                null,
                                null,
                                "GB",
                                LocalDate.of(1843, Month.JANUARY, 1),
                                null,
                                input);
        }

        private static RequestMetadata metadata() {
                return new RequestMetadata(TENANT_ID, "operator", UUID.randomUUID());
        }

        private static NaturalPerson naturalPerson() {
                return NaturalPerson.create(
                                NATURAL_PARTY_ID,
                                TENANT_ID,
                                null,
                                new NaturalPersonDetails(
                                                "Ada",
                                                "Lovelace",
                                                null,
                                                LocalDate.of(1815, Month.DECEMBER, 10),
                                                LocalDate.of(1852, Month.NOVEMBER, 27),
                                                "GB"),
                                TODAY,
                                NOW,
                                "operator");
        }

        private static LegalEntity legalEntity() {
                return LegalEntity.create(
                                LEGAL_PARTY_ID,
                                TENANT_ID,
                                null,
                                new LegalEntityDetails(
                                                "Analytical Engines Ltd",
                                                null,
                                                null,
                                                "GB",
                                                LocalDate.of(1843, Month.JANUARY, 1),
                                                null),
                                TODAY,
                                NOW,
                                "operator");
        }

        private static PartyIdentifier identifier(PartyId partyId) {
                return PartyIdentifier.builder()
                                .identifierId(new PartyIdentifierId(UUID.randomUUID()))
                                .tenantId(TENANT_ID)
                                .partyId(partyId)
                                .identifierSchemeId(SCHEME_ID)
                                .protectedValue(new ProtectedIdentifierValue(
                                                CIPHERTEXT,
                                                1,
                                                VALUE_HASH,
                                                "********1234",
                                                new IdentifierRuleVersion(1)))
                                .issuerCode("issuer")
                                .issuedOn(TODAY.minusYears(1))
                                .expiresOn(TODAY.plusYears(5))
                                .created(NOW, "operator")
                                .build();
        }

        private static IdentifierScheme scheme() {
                return new IdentifierScheme(
                                SCHEME_ID,
                                "GB-NATIONAL-ID",
                                "GB",
                                IdentifierCategory.NATIONAL_ID,
                                IdentifierSubjectType.BOTH,
                                "National identifier",
                                null,
                                "TRIM_UPPERCASE_V1",
                                "ALPHANUMERIC_V1",
                                4,
                                32,
                                false,
                                IdentifierSchemeStatus.ACTIVE,
                                IdentifierSchemeVersion.initial(),
                                AuditInfo.initial(NOW, "catalog"));
        }
}
