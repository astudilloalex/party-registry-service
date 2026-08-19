package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.PORT;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.USE_CASE;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.construct;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.port;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.type;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.useCase;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.value;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CompleteNationalityAndIdentifierDataFlowContractTest {
    private static final UUID TENANT = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a1");
    private static final UUID PROCESS = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a2");
    private static final UUID PARTY = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a3");
    private static final UUID NATIONALITY = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a4");
    private static final UUID SCHEME = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a5");
    private static final UUID IDENTIFIER = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a6");
    private static final UUID OUTBOX = UUID.fromString("078f0c72-4a7b-7c91-8b2a-1234567890a7");
    private static final String PLAINTEXT = "SYNTHETIC-IDENTIFIER-5678";
    private static final String HASH = "B".repeat(64);
    private static final LocalDate ISSUED_ON = LocalDate.of(2020, 1, 2);
    private static final LocalDate EXPIRES_ON = LocalDate.of(2030, 1, 2);
    private static final Instant VERIFIED_AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-17T09:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-18T10:15:30Z");

    @Test
    void addNationalityPropagatesRequiredPrimaryWithTenantVersionAndAtomicOutboxIdentity() {
        var geography = port("GeographicReferencePort")
                .returns("resolveActive", CompletableFuture.completedFuture(countryEvidence()));
        var unitOfWork = port("PartyUnitOfWorkPort")
                .answers("addNationalityAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, invoke(intent, "tenantId"));
                    assertEquals(PARTY, invoke(intent, "partyId"));
                    assertEquals(true, invoke(intent, "primary"));
                    assertEquals(4L, invoke(intent, "expectedVersion"));
                    assertEquals("synthetic-actor", invoke(intent, "actorId"));
                    assertOutbox(intent, "party.nationality-added.v1");
                    return CompletableFuture.completedFuture(mutationResult(PARTY, 5L));
                });

        Object result = invoke(useCase("AddNationalityUseCase", geography.proxy(), unitOfWork.proxy()), "execute",
                context(), PARTY, value("NationalityCommand", "EC", true, ISSUED_ON, null), 4L);

        assertEquals(5L, invoke(result, "version"));
        assertEquals(1, unitOfWork.count("addNationalityAndAppendOutbox"));
    }

    @Test
    void updateNationalityPropagatesRequiredPrimaryWithoutChangingOwnershipOrTransactionIntent() {
        var geography = port("GeographicReferencePort")
                .returns("resolveActive", CompletableFuture.completedFuture(countryEvidence()));
        var unitOfWork = port("PartyUnitOfWorkPort")
                .answers("updateNationalityAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, invoke(intent, "tenantId"));
                    assertEquals(PARTY, invoke(intent, "partyId"));
                    assertEquals(NATIONALITY, invoke(intent, "nationalityId"));
                    assertEquals(false, invoke(intent, "primary"));
                    assertEquals(7L, invoke(intent, "expectedVersion"));
                    assertOutbox(intent, "party.nationality-updated.v1");
                    return CompletableFuture.completedFuture(mutationResult(PARTY, 8L));
                });

        Object result = invoke(useCase("UpdateNationalityUseCase", geography.proxy(), unitOfWork.proxy()), "execute",
                context(), PARTY, NATIONALITY, value("NationalityCommand", "EC", false, ISSUED_ON, null), 7L);

        assertEquals(8L, invoke(result, "version"));
        assertEquals(1, unitOfWork.count("updateNationalityAndAppendOutbox"));
    }

    @Test
    void identifierCreationPropagatesRequiredAndNullableInputFactsWithProtectedImmutableAssociations() {
        var schemes = port("IdentifierSchemeCatalogPort")
                .returns("findUsableById", CompletableFuture.completedFuture(scheme()));
        var rules = port("IdentifierRuleCatalogPort").returns("normalizeAndValidate", normalized());
        var protection = port("IdentifierProtectionPort")
                .returns("protect", CompletableFuture.completedFuture(protectedIdentifier()));
        var unitOfWork = port("IdentifierUnitOfWorkPort")
                .answers("createIdentifierAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, fact(intent, "tenantId"));
                    assertEquals(PARTY, fact(intent, "partyId"));
                    assertEquals(SCHEME, fact(intent, "schemeId", "identifierSchemeId"));
                    assertEquals("SYNTHETIC_ISSUER", fact(intent, "issuerCode"));
                    assertEquals(true, fact(intent, "primary"));
                    assertEquals(ISSUED_ON, fact(intent, "issuedOn"));
                    assertEquals(EXPIRES_ON, fact(intent, "expiresOn"));
                    assertNull(fact(intent, "identifierId"));
                    assertEquals(0L, fact(intent, "expectedVersion"));
                    Object protectedValue = fact(intent, "protectedIdentifier");
                    assertEquals(HASH, invoke(protectedValue, "normalizedValueHash"));
                    assertEquals("****5678", invoke(protectedValue, "maskedValue"));
                    assertOutbox(intent, "party.identifier-created.v1");
                    return CompletableFuture.completedFuture(mutationResult(IDENTIFIER, 0L));
                });
        Object service = useCase("CreatePartyIdentifierUseCase",
                schemes.proxy(), rules.proxy(), protection.proxy(), unitOfWork.proxy());
        Object command = creationCommand(PARTY, SCHEME, PLAINTEXT, "SYNTHETIC_ISSUER", true, ISSUED_ON, EXPIRES_ON);

        Object result = invoke(service, "execute", context(), command);

        assertEquals(IDENTIFIER, invoke(result, "resourceId"));
        assertEquals(1, schemes.count("findUsableById"));
        assertEquals(1, protection.count("protect"));
        assertEquals(1, unitOfWork.count("createIdentifierAndAppendOutbox"));
    }

    @Test
    void identifierCreationCarrierKeepsIssuerAndDatesNullableButPrimaryRequired() {
        Class<?> carrier = creationCarrierType();
        Map<String, Class<?>> components = Arrays.stream(carrier.getRecordComponents())
                .collect(java.util.stream.Collectors.toMap(RecordComponent::getName, RecordComponent::getType));

        assertTrue(components.get("primary") == boolean.class,
                "OpenAPI-required primary must be a non-nullable boolean application fact");
        Object command = creationCommand(PARTY, SCHEME, PLAINTEXT, null, false, null, null);
        assertNull(fact(command, "issuerCode"));
        assertNull(fact(command, "issuedOn"));
        assertNull(fact(command, "expiresOn"));
    }

    @Test
    void verifiedTransitionPropagatesAcceptedStatusAndRequiredVerificationEvidence() {
        Object verified = identifierStatus("VERIFIED");
        Object pending = identifierStatus("PENDING_VERIFICATION");
        Object command = value("IdentifierTransitionCommand",
                pending, verified, ISSUED_ON, EXPIRES_ON, VERIFIED_AT, "synthetic-verifier");
        assertTransition(command, verified, VERIFIED_AT, "synthetic-verifier", EXPIRES_ON,
                "party.identifier-verified.v1", 11L);
    }

    @Test
    void expiredTransitionPropagatesRequiredExpiryAndPreservesNullableVerificationEvidence() {
        Object expired = identifierStatus("EXPIRED");
        Object verified = identifierStatus("VERIFIED");
        Object command = value("IdentifierTransitionCommand",
                verified, expired, ISSUED_ON, EXPIRES_ON, null, null);
        assertTransition(command, expired, null, null, EXPIRES_ON, "party.identifier-expired.v1", 12L);
    }

    @Test
    void identifierUpdatePreservesImmutableAssociationsVersionAndAtomicOutboxIdentity() {
        var unitOfWork = port("IdentifierUnitOfWorkPort")
                .answers("updateIdentifierAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, fact(intent, "tenantId"));
                    assertEquals(IDENTIFIER, fact(intent, "identifierId"));
                    assertEquals(PARTY, fact(intent, "partyId"));
                    assertEquals(SCHEME, fact(intent, "schemeId", "identifierSchemeId"));
                    assertNull(fact(intent, "protectedIdentifier"));
                    assertEquals(9L, fact(intent, "expectedVersion"));
                    assertOutbox(intent, "party.identifier-updated.v1");
                    return CompletableFuture.completedFuture(mutationResult(IDENTIFIER, 10L));
                });
        Object command = value("UpdateIdentifierCommand", "SYNTHETIC_ISSUER", true, ISSUED_ON, EXPIRES_ON);

        invoke(useCase("UpdatePartyIdentifierUseCase", unitOfWork.proxy()), "execute",
                context(), IDENTIFIER, PARTY, SCHEME, command, 9L);

        assertEquals(1, unitOfWork.count("updateIdentifierAndAppendOutbox"));
        Set<String> commandFields = Arrays.stream(command.getClass().getRecordComponents())
                .map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(commandFields.stream().anyMatch(Set.of(
                "identifierId", "tenantId", "partyId", "schemeId", "identifierSchemeId", "value", "plaintext")::contains),
                "Identifier update input must not make immutable identity, ownership, Scheme, or plaintext mutable");
    }

    @Test
    void identifierQueryViewCarriesPersistedOutputAuditAndVersionFactsAndOrdinaryResultIsRestricted() {
        Object view = identifierView();
        assertEquals("PENDING_VERIFICATION", String.valueOf(fact(view, "status")));
        assertEquals(true, fact(view, "primary"));
        assertEquals("SYNTHETIC_ISSUER", fact(view, "issuerCode"));
        assertEquals(ISSUED_ON, fact(view, "issuedOn"));
        assertEquals(EXPIRES_ON, fact(view, "expiresOn"));
        assertNull(fact(view, "verifiedAt"));
        assertNull(fact(view, "verifiedBy"));
        assertEquals(CREATED_AT, auditFact(view, "createdAt"));
        assertEquals("synthetic-creator", auditFact(view, "createdBy"));
        assertEquals(UPDATED_AT, auditFact(view, "updatedAt"));
        assertEquals("synthetic-updater", auditFact(view, "updatedBy"));
        assertEquals(13L, fact(view, "version"));

        var query = port("IdentifierQueryPort")
                .answers("findByTenantAndId", arguments -> {
                    assertEquals(TENANT, arguments[0]);
                    assertEquals(IDENTIFIER, arguments[1]);
                    return CompletableFuture.completedFuture(view);
                });
        Object ordinary = invoke(useCase("GetPartyIdentifierUseCase", query.proxy()), "execute", context(), IDENTIFIER);

        assertEquals("****5678", fact(ordinary, "maskedValue"));
        assertEquals(true, fact(ordinary, "primary"));
        assertEquals("PENDING_VERIFICATION", String.valueOf(fact(ordinary, "status")));
        assertEquals("SYNTHETIC_ISSUER", fact(ordinary, "issuerCode"));
        assertEquals(CREATED_AT, auditFact(ordinary, "createdAt"));
        assertEquals(13L, fact(ordinary, "version"));
        Set<String> ordinaryFields = Arrays.stream(ordinary.getClass().getRecordComponents())
                .map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(ordinaryFields.stream().anyMatch(Set.of(
                "plaintext", "value", "ciphertext", "encryptedValue", "normalizedValueHash")::contains),
                "Ordinary identifier results must expose neither plaintext nor protected persistence values");
    }

    @Test
    void repairedDataFlowIntroducesNeitherSchemeMutationNorCommandIdempotencyCapability() {
        List<String> prohibited = List.of(
                PORT + "IdempotencyPort", PORT + "CommandReplayPort", PORT + "SchemeMutationPort",
                PORT + "SchemeAdministrationPort", USE_CASE + "UpdateIdentifierSchemeUseCase");
        prohibited.forEach(name -> {
            try {
                Class.forName(name);
                fail("Prohibited application capability exists: " + name);
            } catch (ClassNotFoundException expected) {
                // Absence is the approved architecture contract.
            }
        });
    }

    private static void assertTransition(
            Object command,
            Object acceptedStatus,
            Instant verifiedAt,
            String verifiedBy,
            LocalDate expiresOn,
            String eventType,
            long expectedVersion) {
        var unitOfWork = port("IdentifierUnitOfWorkPort")
                .answers("transitionIdentifierAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, fact(intent, "tenantId"));
                    assertEquals(IDENTIFIER, fact(intent, "identifierId"));
                    assertEquals(PARTY, fact(intent, "partyId"));
                    assertEquals(SCHEME, fact(intent, "schemeId", "identifierSchemeId"));
                    assertEquals(expectedVersion, fact(intent, "expectedVersion"));
                    assertNull(fact(intent, "protectedIdentifier"));
                    assertAll("complete accepted transition facts",
                            () -> assertEquals(acceptedStatus,
                                    fact(intent, "status", "targetStatus", "acceptedStatus")),
                            () -> assertEquals(verifiedAt, fact(intent, "verifiedAt")),
                            () -> assertEquals(verifiedBy, fact(intent, "verifiedBy")),
                            () -> assertEquals(expiresOn, fact(intent, "expiresOn")));
                    assertOutbox(intent, eventType);
                    return CompletableFuture.completedFuture(mutationResult(IDENTIFIER, expectedVersion + 1));
                });

        invoke(useCase("TransitionPartyIdentifierStatusUseCase", unitOfWork.proxy()), "execute",
                context(), IDENTIFIER, PARTY, SCHEME, command, expectedVersion);

        assertEquals(1, unitOfWork.count("transitionIdentifierAndAppendOutbox"));
        assertEquals(0, unitOfWork.count("updateIdentifierAndAppendOutbox"));
    }

    private static Object creationCommand(
            UUID partyId,
            UUID schemeId,
            String plaintext,
            String issuerCode,
            boolean primary,
            LocalDate issuedOn,
            LocalDate expiresOn) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("partyId", partyId);
        facts.put("schemeId", schemeId);
        facts.put("identifierSchemeId", schemeId);
        facts.put("value", plaintext);
        facts.put("plaintext", plaintext);
        facts.put("issuerCode", issuerCode);
        facts.put("primary", primary);
        facts.put("issuedOn", issuedOn);
        facts.put("expiresOn", expiresOn);
        return record(creationCarrierType(), facts);
    }

    private static Class<?> creationCarrierType() {
        return Arrays.stream(type(USE_CASE + "CreatePartyIdentifierUseCase").getMethods())
                .filter(method -> method.getName().equals("execute"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .filter(Class::isRecord)
                .filter(candidate -> has(candidate, "partyId")
                        && (has(candidate, "schemeId") || has(candidate, "identifierSchemeId"))
                        && (has(candidate, "value") || has(candidate, "plaintext"))
                        && has(candidate, "issuerCode") && has(candidate, "primary")
                        && has(candidate, "issuedOn") && has(candidate, "expiresOn"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Identifier creation execute boundary must accept one application carrier for party, Scheme, "
                                + "transient value, issuer, primary, issue date, and expiry date"));
    }

    private static Object identifierView() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("id", IDENTIFIER);
        facts.put("tenantId", TENANT);
        facts.put("partyId", PARTY);
        facts.put("schemeId", SCHEME);
        facts.put("identifierSchemeId", SCHEME);
        facts.put("issuerCode", "SYNTHETIC_ISSUER");
        facts.put("ciphertext", "synthetic-ciphertext");
        facts.put("encryptedValue", "synthetic-ciphertext");
        facts.put("normalizedValueHash", HASH);
        facts.put("maskedValue", "****5678");
        facts.put("encryptionKeyVersion", 2);
        facts.put("normalizationVersion", 1);
        facts.put("primary", true);
        facts.put("status", identifierStatus("PENDING_VERIFICATION"));
        facts.put("issuedOn", ISSUED_ON);
        facts.put("expiresOn", EXPIRES_ON);
        facts.put("verifiedAt", null);
        facts.put("verifiedBy", null);
        facts.put("createdAt", CREATED_AT);
        facts.put("createdBy", "synthetic-creator");
        facts.put("updatedAt", UPDATED_AT);
        facts.put("updatedBy", "synthetic-updater");
        facts.put("version", 13L);
        return record(type(APPLICATION + "PartyIdentifierView"), facts);
    }

    private static Object record(Class<?> recordType, Map<String, Object> facts) {
        assertTrue(recordType.isRecord(), recordType.getName() + " must be a boundary-neutral record");
        RecordComponent[] components = recordType.getRecordComponents();
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            if (component.getName().equals("audit")) {
                arguments[index] = record(component.getType(), facts);
            } else if (facts.containsKey(component.getName())) {
                arguments[index] = facts.get(component.getName());
            } else {
                throw new AssertionError("No approved synthetic fact supplied for "
                        + recordType.getSimpleName() + "." + component.getName());
            }
        }
        try {
            Constructor<?> constructor = recordType.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot construct boundary-neutral record " + recordType.getName(), exception);
        }
    }

    private static Object fact(Object target, String... names) {
        for (String name : names) {
            Method direct = method(target.getClass(), name);
            if (direct != null) {
                return invoke(target, name);
            }
        }
        Method mutation = method(target.getClass(), "mutation");
        if (mutation != null) {
            Object nested = invoke(target, "mutation");
            if (nested != null && nested != target) {
                if (nested instanceof Enum<?> && Arrays.stream(names).anyMatch(name ->
                        name.equals("status") || name.equals("targetStatus") || name.equals("acceptedStatus"))) {
                    return nested;
                }
                for (String name : names) {
                    Method nestedMethod = method(nested.getClass(), name);
                    if (nestedMethod != null) {
                        return invoke(nested, name);
                    }
                }
            }
        }
        throw new AssertionError(target.getClass().getSimpleName() + " does not preserve required fact "
                + String.join("/", names));
    }

    private static Object auditFact(Object target, String name) {
        Method direct = method(target.getClass(), name);
        if (direct != null) {
            return invoke(target, name);
        }
        Method audit = method(target.getClass(), "audit");
        if (audit != null) {
            return invoke(invoke(target, "audit"), name);
        }
        throw new AssertionError(target.getClass().getSimpleName() + " does not preserve audit fact " + name);
    }

    private static Method method(Class<?> owner, String name) {
        return Arrays.stream(owner.getMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == 0)
                .findFirst().orElse(null);
    }

    private static boolean has(Class<?> owner, String name) {
        return method(owner, name) != null;
    }

    private static void assertOutbox(Object intent, String eventType) {
        Object outbox = fact(intent, "outbox");
        assertEquals(eventType, invoke(outbox, "eventType"));
        assertEquals(PROCESS, invoke(outbox, "correlationId"));
    }

    private static Object context() {
        return construct(APPLICATION + "RequestContext", TENANT.toString(), "synthetic-actor", PROCESS.toString(),
                (Supplier<UUID>) () -> PROCESS);
    }

    private static Object countryEvidence() {
        return value("CountryEvidence", "EC", Instant.parse("2026-08-18T09:00:00Z"));
    }

    private static Object scheme() {
        return value("IdentifierSchemeMetadata", SCHEME, "SYNTHETIC_SCHEME", "ACTIVE", "NATURAL_PERSON",
                "trim-v1", "synthetic-v1", 5, 64, false);
    }

    private static Object normalized() {
        return value("NormalizedIdentifier", "SYNTHETICIDENTIFIER5678", 1);
    }

    private static Object protectedIdentifier() {
        return value("ProtectedIdentifierData", "synthetic-ciphertext", HASH, "****5678", 2);
    }

    private static Object mutationResult(UUID resourceId, long version) {
        return value("MutationResult", resourceId, version, OUTBOX, UPDATED_AT);
    }

    private static Object identifierStatus(String constant) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object status = Enum.valueOf((Class<? extends Enum>) type(
                "com.alexastudillo.partyregistry.domain.PartyIdentifierStatus").asSubclass(Enum.class), constant);
        return status;
    }
}
