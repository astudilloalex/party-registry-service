package com.alexastudillo.partyregistry.infrastructure.configuration;

import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentPartyRegistrationPort;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyCursorPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierRegistrationPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyLifecycleUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetPartyUseCase;
import com.alexastudillo.partyregistry.application.usecase.ListPartiesUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchPartyUseCase;
import com.alexastudillo.partyregistry.application.usecase.CreateLegalEntityUseCase;
import com.alexastudillo.partyregistry.application.usecase.CreateNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import jakarta.enterprise.inject.Produces;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies runtime CDI composition selects the identifier-required application paths.
 */
class ApplicationUseCaseProducerTest {

    @Test
    void wiresNaturalRegistrationThroughHmacAndAtomicRegistrationPorts() throws ReflectiveOperationException {
        Method producer = ApplicationUseCaseProducer.class.getDeclaredMethod(
                "createNaturalPersonUseCase",
                RegistrationFingerprintPort.class,
                IdempotentPartyRegistrationPort.class,
                CountryReferencePort.class,
                IdentifierSchemeRepository.class,
                IdentifierProtectionPort.class,
                OperationObservationPort.class);

        assertProducer(producer, CreateNaturalPersonUseCase.class);
        assertEquals(1, CreateNaturalPersonUseCase.class.getDeclaredConstructors().length);
    }

    @Test
    void exposesAllNewUseCasesAsProducerMethods() throws ReflectiveOperationException {
        assertProducer(ApplicationUseCaseProducer.class.getDeclaredMethod(
                "createLegalEntityUseCase",
                RegistrationFingerprintPort.class,
                IdempotentPartyRegistrationPort.class,
                CountryReferencePort.class,
                IdentifierSchemeRepository.class,
                IdentifierProtectionPort.class,
                OperationObservationPort.class), CreateLegalEntityUseCase.class);
        assertEquals(1, CreateLegalEntityUseCase.class.getDeclaredConstructors().length);
        assertProducer(ApplicationUseCaseProducer.class.getDeclaredMethod(
                "registerPartyIdentifierUseCase",
                PartyLookupPort.class,
                PartyIdentifierRegistrationPort.class,
                IdentifierSchemeRepository.class,
                IdentifierProtectionPort.class,
                OperationObservationPort.class), RegisterPartyIdentifierUseCase.class);
        assertProducer(ApplicationUseCaseProducer.class.getDeclaredMethod(
                "changePartyLifecycleUseCase",
                PartyMutationPort.class,
                OperationObservationPort.class), ChangePartyLifecycleUseCase.class);
        assertProducer(ApplicationUseCaseProducer.class.getDeclaredMethod("getPartyUseCase", PartyQueryPort.class, OperationObservationPort.class), GetPartyUseCase.class);
        assertProducer(ApplicationUseCaseProducer.class.getDeclaredMethod("listPartiesUseCase", PartyQueryPort.class, PartyCursorPort.class, OperationObservationPort.class), ListPartiesUseCase.class);
        assertProducer(ApplicationUseCaseProducer.class.getDeclaredMethod("patchPartyUseCase", PartyMutationPort.class, OperationObservationPort.class), PatchPartyUseCase.class);
    }

    private static void assertProducer(Method method, Class<?> returnType) {
        assertTrue(method.isAnnotationPresent(Produces.class));
        assertEquals(returnType, method.getReturnType());
        assertEquals(List.of(), Arrays.stream(method.getExceptionTypes()).toList());
    }
}
