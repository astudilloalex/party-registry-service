package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the observation port cannot accept payload or sensitive identifier material.
 */
class OperationObservationPortContractTest {

    private static final Set<Class<?>> ALLOWED_INPUT_TYPES = Set.of(
            RequestMetadata.class,
            ObservedOperation.class,
            OperationOutcome.class);
    private static final Set<String> FORBIDDEN_NAME_FRAGMENTS = Set.of(
            "value",
            "mask",
            "scheme",
            "fingerprint",
            "cipher",
            "key",
            "version",
            "payload",
            "name",
            "date");

    @Test
    void acceptsOnlyTrustedContextAndClosedOperationOutcomeEnums()
            throws ReflectiveOperationException {
        Method start = OperationObservationPort.class.getMethod(
                "start",
                RequestMetadata.class,
                ObservedOperation.class);
        Method complete = OperationObservationPort.StartedObservation.class.getMethod(
                "complete",
                OperationOutcome.class);

        assertEquals(OperationObservationPort.StartedObservation.class, start.getReturnType());
        assertEquals(void.class, complete.getReturnType());
        assertTrue(ObservedOperation.class.isEnum());
        assertTrue(OperationOutcome.class.isEnum());
        assertTrue(Modifier.isFinal(ObservedOperation.class.getModifiers()));
        assertTrue(Modifier.isFinal(OperationOutcome.class.getModifiers()));

        for (Method method : ListOfMethods.values()) {
            Arrays.stream(method.getParameterTypes()).forEach(type -> {
                assertTrue(ALLOWED_INPUT_TYPES.contains(type), type.getTypeName());
                assertFalse(type == String.class || type == Object.class || Map.class.isAssignableFrom(type));
            });
            Arrays.stream(method.getParameters()).forEach(parameter -> {
                String normalizedName = parameter.getName().toLowerCase();
                assertFalse(FORBIDDEN_NAME_FRAGMENTS.stream().anyMatch(normalizedName::contains),
                        normalizedName);
            });
        }
    }

    @Test
    void declaresNoPayloadFieldsOnTheObserverContracts() {
        assertNoState(OperationObservationPort.class);
        assertNoState(OperationObservationPort.StartedObservation.class);
    }

    private static void assertNoState(Class<?> contract) {
        assertEquals(0L, Arrays.stream(contract.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !field.isEnumConstant())
                .map(Field::getType)
                .count());
    }

    /**
     * Supplies every observer contract method to the API-shape assertions.
     */
    private static final class ListOfMethods {

        private ListOfMethods() {
        }

        private static Method[] values() throws ReflectiveOperationException {
            return new Method[]{
                    OperationObservationPort.class.getMethod(
                            "start",
                            RequestMetadata.class,
                            ObservedOperation.class),
                    OperationObservationPort.StartedObservation.class.getMethod(
                            "complete",
                            OperationOutcome.class)
            };
        }
    }
}
