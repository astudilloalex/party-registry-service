package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.PORT;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.USE_CASE;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.type;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.PartyType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class PartyDetailsMutationQuerySeparationContractTest {
    private static final UUID PARTY = UUID.fromString("058f0c72-4a7b-7c91-8b2a-3234567890a1");

    @Test
    void mutationInputIsAnUnauditedApplicationRecordDistinctFromTheAuditedQueryView() {
        Class<?> inputType = type(APPLICATION + "PartyDetailsInput");
        Class<?> viewType = type(APPLICATION + "PartyDetailsView");

        assertTrue(inputType.isRecord(), "mutation input must be an immutable boundary-neutral record");
        assertNotEquals(viewType, inputType, "mutation input must not reuse audited query output");
        assertEquals(List.of(
                        "partyId", "kind", "primaryName", "secondaryName", "optionalName",
                        "countryCode", "startDate", "endDate"),
                componentNames(inputType));
        assertFalse(componentNames(inputType).contains("audit"),
                "mutation callers must never fabricate persistence audit facts");

        Object input = input();
        assertEquals(PARTY, invoke(input, "partyId"));
        assertEquals(DetailKind.NATURAL_PERSON, invoke(input, "kind"));
        assertEquals("  Synthetic ", invoke(input, "primaryName"));
        assertEquals(" Person  ", invoke(input, "secondaryName"));
        assertEquals("Preferred Synthetic", invoke(input, "optionalName"));
        assertEquals("EC", invoke(input, "countryCode"));
        assertEquals(LocalDate.of(1990, 2, 3), invoke(input, "startDate"));
        assertNull(invoke(input, "endDate"));
    }

    @Test
    void createUpdateAndCanonicalDisplayNameMutationContractsConsumeOnlyTheMutationInput() {
        Class<?> inputType = type(APPLICATION + "PartyDetailsInput");

        assertEquals(inputType, recordComponentType("CreatePartyCommand", "details"));
        assertEquals(inputType, recordComponentType("PartyDetailsMutation", "details"));
        assertEquals(inputType, parameterType("PartyCreationMutation", "from", 2, 1));
        assertEquals(inputType, parameterType("PartyDetailsMutation", "from", 1, 0));
        assertEquals(inputType, parameterType(USE_CASE + "UpdatePartyDetailsUseCase", "execute", 4, 2));

        Object input = input();
        Object command = value("CreatePartyCommand", PartyType.NATURAL_PERSON, input);
        Object mutation = invoke(value("PartyDetailsMutation", input, "Synthetic Person"), "details");
        assertSame(input, invoke(command, "details"));
        assertSame(input, mutation);
    }

    @Test
    void tenantQualifiedQueriesContinueReturningTheDistinctAuditedPartyDetailsView() {
        Class<?> inputType = type(APPLICATION + "PartyDetailsInput");
        Class<?> viewType = type(APPLICATION + "PartyDetailsView");
        Class<?> auditType = type(APPLICATION + "AuditFacts");

        assertNotEquals(inputType, viewType);
        assertEquals(auditType, recordComponentType("PartyDetailsView", "audit"));
        assertEquals(CompletionStage.class,
                method(PORT + "PartyQueryPort", "findDetails", 2).getReturnType());
        assertTrue(method(PORT + "PartyQueryPort", "findDetails", 2)
                        .getGenericReturnType().getTypeName().contains("PartyDetailsView"),
                "query port must retain PartyDetailsView output");
        assertTrue(method(USE_CASE + "GetPartyDetailsUseCase", "execute", 2)
                        .getGenericReturnType().getTypeName().contains("PartyDetailsView"),
                "query use case must retain PartyDetailsView output");
    }

    private static Object input() {
        return value("PartyDetailsInput",
                PARTY,
                DetailKind.NATURAL_PERSON,
                "  Synthetic ",
                " Person  ",
                "Preferred Synthetic",
                "EC",
                LocalDate.of(1990, 2, 3),
                null);
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static Class<?> recordComponentType(String simpleName, String componentName) {
        return Arrays.stream(type(APPLICATION + simpleName).getRecordComponents())
                .filter(component -> component.getName().equals(componentName))
                .map(RecordComponent::getType)
                .findFirst()
                .orElseThrow(() -> new AssertionError(simpleName + " must carry " + componentName));
    }

    private static Class<?> parameterType(String simpleName, String methodName, int count, int index) {
        String qualifiedName = simpleName.contains(".") ? simpleName : APPLICATION + simpleName;
        return method(qualifiedName, methodName, count).getParameterTypes()[index];
    }

    private static Method method(String qualifiedName, String methodName, int parameterCount) {
        return Arrays.stream(type(qualifiedName).getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.getParameterCount() == parameterCount)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        qualifiedName + " must expose " + methodName + " with " + parameterCount + " parameters"));
    }
}
