package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.PORT;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.USE_CASE;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.type;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ApplicationPortInventoryContractTest {
    @Test
    void approvedInputUseCaseInventoryIsCompleteAndContainsNoSchemeAdministration() {
        List<String> required = List.of(
                "CreatePartyUseCase",
                "SearchPartiesUseCase",
                "GetPartyUseCase",
                "UpdatePartyUseCase",
                "TransitionPartyStatusUseCase",
                "GetPartyDetailsUseCase",
                "UpdatePartyDetailsUseCase",
                "AddNationalityUseCase",
                "SearchNationalitiesUseCase",
                "GetNationalityUseCase",
                "UpdateNationalityUseCase",
                "EndNationalityUseCase",
                "CreatePartyIdentifierUseCase",
                "SearchPartyIdentifiersUseCase",
                "GetPartyIdentifierUseCase",
                "FindPartyIdentifiersByPartyAndSchemeUseCase",
                "ExactIdentifierSearchUseCase",
                "UpdatePartyIdentifierUseCase",
                "TransitionPartyIdentifierStatusUseCase",
                "DecryptPartyIdentifierUseCase",
                "PublishOutboxBatchUseCase",
                "RecoverFailedOutboxEventUseCase");

        required.forEach(name -> {
            Class<?> useCase = type(USE_CASE + name);
            assertTrue(java.util.stream.Stream.of(useCase.getMethods())
                    .anyMatch(method -> method.getName().equals("execute")
                            && Modifier.isPublic(method.getModifiers())
                            && CompletionStage.class.isAssignableFrom(method.getReturnType())),
                    name + " must expose a public execute boundary");
        });
    }

    @Test
    void requiredPortsAreApplicationOwnedInterfaces() {
        List<String> required = List.of(
                "PartyQueryPort",
                "PartyUnitOfWorkPort",
                "IdentifierQueryPort",
                "IdentifierUnitOfWorkPort",
                "IdentifierSchemeCatalogPort",
                "IdentifierRuleCatalogPort",
                "GeographicReferencePort",
                "IdentifierProtectionPort",
                "DecryptionSecurityLogPort",
                "OutboxStorePort",
                "IntegrationEventPublisherPort");

        required.forEach(name -> assertTrue(type(PORT + name).isInterface(), name + " must be an interface"));
    }

    @Test
    void schemeCatalogIsQueryOnlyAndCannotEmitOutboxEffects() {
        Class<?> catalog = type(PORT + "IdentifierSchemeCatalogPort");
        Set<String> methods = Set.of(catalog.getMethods()).stream().map(Method::getName).collect(Collectors.toSet());

        assertTrue(methods.contains("findUsableById"));
        methods.forEach(method -> assertFalse(
                method.toLowerCase(Locale.ROOT).matches(".*(create|save|insert|update|delete|activate|retire|publish|outbox).*"),
                "Scheme catalog exposes prohibited mutation method: " + method));
    }

    @Test
    void mutationPortsExposeAtomicMutationAndOutboxIntentRatherThanSeparateWrites() {
        Set<String> partyMethods = java.util.stream.Stream.of(type(PORT + "PartyUnitOfWorkPort").getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> identifierMethods = java.util.stream.Stream.of(type(PORT + "IdentifierUnitOfWorkPort").getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(partyMethods.containsAll(Set.of(
                "createPartyAndAppendOutbox",
                "updatePartyAndAppendOutbox",
                "transitionPartyAndAppendOutbox",
                "updateDetailsAndAppendOutbox",
                "addNationalityAndAppendOutbox",
                "updateNationalityAndAppendOutbox",
                "endNationalityAndAppendOutbox")));
        assertTrue(identifierMethods.containsAll(Set.of(
                "createIdentifierAndAppendOutbox",
                "updateIdentifierAndAppendOutbox",
                "transitionIdentifierAndAppendOutbox")));
        java.util.stream.Stream.concat(partyMethods.stream(), identifierMethods.stream()).forEach(method -> {
            assertFalse(method.equals("appendOutbox"), "outbox must not be a separate application write");
            assertFalse(method.equals("save"), "generic save cannot express mutation+outbox atomicity");
        });
    }

    @Test
    void prohibitedPortsAndRuntimeUseCasesDoNotExist() {
        List<String> prohibited = List.of(
                PORT + "IdempotencyPort",
                PORT + "CommandReplayPort",
                PORT + "SchemeMutationPort",
                PORT + "SchemeAdministrationPort",
                PORT + "ExternalLoggingPort",
                USE_CASE + "CreateIdentifierSchemeUseCase",
                USE_CASE + "UpdateIdentifierSchemeUseCase",
                USE_CASE + "TransitionIdentifierSchemeUseCase",
                USE_CASE + "DeleteIdentifierSchemeUseCase");

        prohibited.forEach(name -> assertThrows(ClassNotFoundException.class, () -> Class.forName(name),
                name + " is prohibited by Amendments 001/002"));
    }

    @Test
    void applicationContractsDoNotExposeAdapterOrQuarkusTypes() {
        List<Class<?>> contracts = List.of(
                type(APPLICATION + "RequestContext"),
                type(PORT + "PartyUnitOfWorkPort"),
                type(PORT + "IdentifierSchemeCatalogPort"),
                type(PORT + "IdentifierProtectionPort"),
                type(PORT + "OutboxStorePort"));

        contracts.stream()
                .flatMap(contract -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(contract.getConstructors())
                                .flatMap(constructor -> java.util.stream.Stream.of(constructor.getParameterTypes())),
                        java.util.stream.Stream.of(contract.getMethods()).flatMap(method -> java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(method.getReturnType()),
                                java.util.stream.Stream.of(method.getParameterTypes())))))
                .map(Class::getName)
                .forEach(name -> {
                    assertFalse(name.contains(".adapter."), "adapter type leaked into application contract: " + name);
                    assertFalse(name.startsWith("io.quarkus."), "Quarkus type leaked into application contract: " + name);
                    assertFalse(name.startsWith("jakarta."), "Jakarta type leaked into application contract: " + name);
                });
    }
}
