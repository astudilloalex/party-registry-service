package com.alexastudillo.partyregistry.infrastructure.configuration;

import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCase;
import com.alexastudillo.partyregistry.application.usecase.CreateIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetIdentifierSchemeUseCase;
import com.alexastudillo.partyregistry.application.usecase.ListIdentifierSchemesUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchIdentifierSchemeUseCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies the real composition root resolves every scheme workflow and its unique reactive collaborators. */
@QuarkusTest
class IdentifierSchemeUseCaseCompositionTest {
    @Inject Instance<CreateIdentifierSchemeUseCase> creates;
    @Inject Instance<GetIdentifierSchemeUseCase> gets;
    @Inject Instance<ListIdentifierSchemesUseCase> lists;
    @Inject Instance<PatchIdentifierSchemeUseCase> patches;
    @Inject Instance<ChangeIdentifierSchemeLifecycleUseCase> lifecycle;

    @Test
    void resolvesAllPlainUseCasesAndKeepsRegistrationLookupUnambiguous() {
        for (Instance<?> workflow : new Instance<?>[] {creates, gets, lists, patches, lifecycle}) {
            assertFalse(workflow.isUnsatisfied());
            assertFalse(workflow.isAmbiguous());
            assertNotNull(workflow.get());
        }
        for (Class<?> type : new Class<?>[] {CreateIdentifierSchemeUseCase.class, GetIdentifierSchemeUseCase.class,
                ListIdentifierSchemesUseCase.class, PatchIdentifierSchemeUseCase.class,
                ChangeIdentifierSchemeLifecycleUseCase.class, IdentifierSchemeReadPort.class,
                IdentifierSchemeMutationPort.class, IdentifierSchemeCursorPort.class,
                IdentifierSchemeRepository.class, OperationObservationPort.class, Clock.class}) {
            var bean = CDI.current().select(type);
            assertFalse(bean.isUnsatisfied(), type.getName());
            assertFalse(bean.isAmbiguous(), type.getName());
            assertNotNull(bean.get());
        }
        assertEquals(ZoneOffset.UTC, CDI.current().select(Clock.class).get().getZone());
    }
}
