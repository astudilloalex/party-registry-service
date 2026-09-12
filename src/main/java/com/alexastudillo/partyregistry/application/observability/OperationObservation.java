package com.alexastudillo.partyregistry.application.observability;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Observes returned Mutiny operations without changing their terminal signals.
 */
public final class OperationObservation {

    private static final OperationObservationPort.StartedObservation NO_OBSERVATION = ignored -> {
    };

    private OperationObservation() {
    }

    /**
     * Wraps one lazily-created operation and records exactly one terminal outcome per subscription.
     *
     * @param requestMetadata trusted request context
     * @param operation bounded operation identity
     * @param observationPort observation output port
     * @param operationSupplier lazy reactive operation
     * @param successClassifier safe classifier for successful items
     * @param <T> emitted item type
     * @return a Uni preserving the original item, failure, and cancellation
     */
    public static <T> Uni<T> observe(
            RequestMetadata requestMetadata,
            ObservedOperation operation,
            OperationObservationPort observationPort,
            Supplier<Uni<T>> operationSupplier,
            Function<? super T, OperationOutcome> successClassifier) {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(observationPort, "observationPort");
        Objects.requireNonNull(operationSupplier, "operationSupplier");
        Objects.requireNonNull(successClassifier, "successClassifier");

        return Uni.createFrom().deferred(() -> {
            OperationObservationPort.StartedObservation observation = startSafely(
                    observationPort,
                    requestMetadata,
                    operation);
            AtomicBoolean completed = new AtomicBoolean();
            Uni<T> observedOperation;
            try {
                observedOperation = Objects.requireNonNull(
                        operationSupplier.get(),
                        "operationSupplier returned null");
            } catch (Throwable failure) {
                observedOperation = Uni.createFrom().failure(failure);
            }
            return observedOperation.onTermination().invoke((item, failure, cancelled) -> {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                OperationOutcome outcome = terminalOutcome(
                        item,
                        failure,
                        cancelled,
                        successClassifier);
                completeSafely(observation, outcome);
            });
        });
    }

    /**
     * Maps a registration result to its bounded created or replayed outcome.
     *
     * @param result safe registration result
     * @return bounded registration outcome
     */
    public static OperationOutcome registrationOutcome(PartyRegistrationResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result.outcome()) {
            case PartyRegistrationOutcome.CREATED -> OperationOutcome.CREATED;
            case PartyRegistrationOutcome.REPLAYED -> OperationOutcome.REPLAYED;
        };
    }

    private static OperationObservationPort.StartedObservation startSafely(
            OperationObservationPort observationPort,
            RequestMetadata requestMetadata,
            ObservedOperation operation) {
        try {
            OperationObservationPort.StartedObservation observation = observationPort.start(
                    requestMetadata,
                    operation);
            return observation == null ? NO_OBSERVATION : observation;
        } catch (Throwable ignored) {
            return NO_OBSERVATION;
        }
    }

    private static <T> OperationOutcome terminalOutcome(
            T item,
            Throwable failure,
            boolean cancelled,
            Function<? super T, OperationOutcome> successClassifier) {
        if (cancelled || containsCancellation(failure)) {
            return OperationOutcome.CANCELLED;
        }
        if (failure != null) {
            return classifyFailure(failure);
        }
        try {
            OperationOutcome outcome = successClassifier.apply(item);
            return outcome == null ? OperationOutcome.INTERNAL_FAILURE : outcome;
        } catch (Throwable ignored) {
            return OperationOutcome.INTERNAL_FAILURE;
        }
    }

    private static OperationOutcome classifyFailure(Throwable failure) {
        ApplicationException applicationException = findApplicationException(failure);
        if (applicationException == null) {
            return OperationOutcome.INTERNAL_FAILURE;
        }
        return switch (applicationException.failure()) {
            case ApplicationFailure.NaturalPersonNotFound _,
                    ApplicationFailure.PartyNotFound _ -> OperationOutcome.NOT_FOUND;
            case ApplicationFailure.IdempotencyKeyConflict _,
                    ApplicationFailure.InvalidPartyLifecycle _ -> OperationOutcome.CONFLICT;
            case ApplicationFailure.ExpectedVersionMismatch _,
                    ApplicationFailure.StalePartyVersion _ -> OperationOutcome.PRECONDITION_FAILED;
            case ApplicationFailure.UnrecognizedBirthCountry _,
                    ApplicationFailure.UnrecognizedIncorporationCountry _,
                    ApplicationFailure.UnknownIdentifierScheme _,
                    ApplicationFailure.InactiveIdentifierScheme _,
                    ApplicationFailure.IncompatibleIdentifierScheme _,
                    ApplicationFailure.IdentifierValidationFailure _,
                    ApplicationFailure.MissingQualifyingIdentifier _,
                    ApplicationFailure.InvalidBusinessState _ -> OperationOutcome.VALIDATION_FAILED;
            case ApplicationFailure.IdentifierUniquenessConflict _ -> OperationOutcome.IDENTIFIER_CONFLICT;
            case ApplicationFailure.DependencyUnavailable _ -> OperationOutcome.DEPENDENCY_UNAVAILABLE;
            case ApplicationFailure.PersistenceFailure _,
                    ApplicationFailure.IdentifierCatalogFailure _ -> OperationOutcome.INTERNAL_FAILURE;
        };
    }

    private static ApplicationException findApplicationException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApplicationException applicationException) {
                return applicationException;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean containsCancellation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void completeSafely(
            OperationObservationPort.StartedObservation observation,
            OperationOutcome outcome) {
        try {
            observation.complete(outcome);
        } catch (Throwable ignored) {
            // Telemetry must not replace a business terminal signal.
        }
    }
}
