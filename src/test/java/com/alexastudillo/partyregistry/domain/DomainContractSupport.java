package com.alexastudillo.partyregistry.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.function.Executable;

final class DomainContractSupport {
    private static final String DOMAIN_PACKAGE = "com.alexastudillo.partyregistry.domain.";

    private DomainContractSupport() {}

    static Class<?> type(String simpleName) {
        try {
            return Class.forName(DOMAIN_PACKAGE + simpleName);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Required domain type is missing: " + simpleName, exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object enumValue(String type, String constant) {
        return Enum.valueOf((Class<? extends Enum>) type(type).asSubclass(Enum.class), constant);
    }

    static Object construct(String type, Object... arguments) {
        Constructor<?> constructor = Arrays.stream(type(type).getConstructors())
                .filter(candidate -> compatible(candidate.getParameterTypes(), arguments))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No compatible public constructor on " + type));
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot construct domain type " + type, exception);
        }
    }

    static Object invokeStatic(String type, String method, Object... arguments) {
        return invokeMethod(null, type(type), method, arguments);
    }

    static Object invoke(Object target, String method, Object... arguments) {
        assertNotNull(target, "domain target");
        return invokeMethod(target, target.getClass(), method, arguments);
    }

    static void assertDomainViolation(Executable operation) {
        try {
            operation.execute();
            fail("Expected a DomainViolation");
        } catch (Throwable failure) {
            if (failure instanceof AssertionError assertionError) {
                throw assertionError;
            }
            assertTrue(
                    type("DomainViolation").isInstance(failure),
                    () -> "Expected DomainViolation but got " + failure.getClass().getName());
            assertNotNull(failure.getMessage(), "domain violation message");
            assertFalse(failure.getMessage().isBlank(), "domain violation message must be diagnostic");
        }
    }

    private static Object invokeMethod(Object target, Class<?> owner, String method, Object[] arguments) {
        Method candidate = Arrays.stream(owner.getMethods())
                .filter(item -> item.getName().equals(method))
                .filter(item -> target != null || Modifier.isStatic(item.getModifiers()))
                .filter(item -> compatible(item.getParameterTypes(), arguments))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No compatible method " + owner.getSimpleName() + "." + method));
        try {
            return candidate.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot invoke domain method " + owner.getSimpleName() + "." + method, exception);
        }
    }

    private static boolean compatible(Class<?>[] parameterTypes, Object[] arguments) {
        if (parameterTypes.length != arguments.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            if (arguments[index] == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
            } else if (!boxed(parameterTypes[index]).isInstance(arguments[index])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            default -> type;
        };
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("Domain method raised a checked exception", failure);
    }
}
