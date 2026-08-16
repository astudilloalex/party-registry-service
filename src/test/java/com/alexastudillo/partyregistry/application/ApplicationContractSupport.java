package com.alexastudillo.partyregistry.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.junit.jupiter.api.function.Executable;

final class ApplicationContractSupport {
    static final String APPLICATION = "com.alexastudillo.partyregistry.application.";
    static final String PORT = APPLICATION + "port.";
    static final String USE_CASE = APPLICATION + "usecase.";

    private ApplicationContractSupport() {}

    static Class<?> type(String qualifiedName) {
        try {
            return Class.forName(qualifiedName);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Required application type is missing: " + qualifiedName, exception);
        }
    }

    static Object value(String simpleName, Object... arguments) {
        return construct(APPLICATION + simpleName, arguments);
    }

    static Object construct(String qualifiedName, Object... arguments) {
        Class<?> owner = type(qualifiedName);
        Constructor<?> constructor = Arrays.stream(owner.getConstructors())
                .filter(candidate -> compatible(candidate.getParameterTypes(), arguments))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No compatible public constructor on " + qualifiedName));
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot construct " + qualifiedName, exception);
        }
    }

    static Object useCase(String simpleName, Object... ports) {
        return construct(USE_CASE + simpleName, ports);
    }

    static Object invoke(Object target, String method, Object... arguments) {
        assertNotNull(target, "application target");
        Method candidate = Arrays.stream(target.getClass().getMethods())
                .filter(item -> item.getName().equals(method))
                .filter(item -> compatible(item.getParameterTypes(), arguments))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No compatible method " + target.getClass().getSimpleName() + "." + method));
        try {
            Object result = candidate.invoke(target, arguments);
            if (result instanceof CompletionStage<?> stage) {
                return stage.toCompletableFuture().join();
            }
            return result;
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot invoke application method " + method, exception);
        }
    }

    static void assertFailureCode(String expectedCode, Executable executable) {
        try {
            executable.execute();
            fail("Expected application failure " + expectedCode);
        } catch (Throwable raw) {
            Throwable failure = unwrap(raw);
            if (failure instanceof AssertionError assertionError) {
                throw assertionError;
            }
            assertEquals(APPLICATION + "ApplicationFailure", failure.getClass().getName());
            assertEquals(expectedCode, String.valueOf(invoke(failure, "code")));
            assertNotNull(failure.getMessage(), "safe diagnostic message");
            assertTrue(!failure.getMessage().isBlank(), "safe diagnostic message must not be blank");
        }
    }

    static RecordingPort port(String simpleName) {
        return new RecordingPort(PORT + simpleName);
    }

    static Object enumValue(String simpleName, String constant) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) type(APPLICATION + simpleName).asSubclass(Enum.class), constant);
        return value;
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

    private static RuntimeException propagate(Throwable raw) {
        Throwable failure = unwrap(raw);
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("Application boundary raised a checked exception", failure);
    }

    private static Throwable unwrap(Throwable raw) {
        Throwable failure = raw;
        while ((failure instanceof InvocationTargetException || failure instanceof CompletionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    static final class RecordingPort implements InvocationHandler {
        private final Class<?> contract;
        private final Object proxy;
        private final List<String> sharedSequence;
        private final List<Call> calls = new ArrayList<>();
        private final Map<String, Function<Object[], Object>> behavior = new HashMap<>();

        RecordingPort(String qualifiedName) {
            this(qualifiedName, new ArrayList<>());
        }

        RecordingPort(String qualifiedName, List<String> sharedSequence) {
            contract = type(qualifiedName);
            assertTrue(contract.isInterface(), qualifiedName + " must be an application-owned interface");
            this.sharedSequence = sharedSequence;
            proxy = Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract}, this);
        }

        Object proxy() {
            return proxy;
        }

        RecordingPort returns(String method, Object value) {
            behavior.put(method, ignored -> value);
            return this;
        }

        RecordingPort answers(String method, Function<Object[], Object> answer) {
            behavior.put(method, answer);
            return this;
        }

        int count(String method) {
            return (int) calls.stream().filter(call -> call.method().equals(method)).count();
        }

        Object argument(String method, int invocation, int argument) {
            return calls.stream()
                    .filter(call -> call.method().equals(method))
                    .skip(invocation)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No invocation " + method + "[" + invocation + "]"))
                    .arguments()[argument];
        }

        List<String> sequence() {
            return List.copyOf(sharedSequence);
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "RecordingPort[" + contract.getSimpleName() + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                };
            }
            Object[] safeArguments = arguments == null ? new Object[0] : arguments.clone();
            calls.add(new Call(method.getName(), safeArguments));
            sharedSequence.add(contract.getSimpleName() + "." + method.getName());
            Function<Object[], Object> answer = behavior.get(method.getName());
            if (answer == null) {
                throw new AssertionError("Unexpected port call " + contract.getSimpleName() + "." + method.getName());
            }
            return answer.apply(safeArguments);
        }

        private record Call(String method, Object[] arguments) {}
    }

    static RecordingPort sequencedPort(String simpleName, List<String> sequence) {
        return new RecordingPort(PORT + simpleName, sequence);
    }
}
