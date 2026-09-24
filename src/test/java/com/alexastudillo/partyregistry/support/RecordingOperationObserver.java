package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records trusted contexts and bounded outcomes for observability boundary tests.
 */
public final class RecordingOperationObserver implements OperationObservationPort {

    private final List<StartedCall> startedCalls = new CopyOnWriteArrayList<>();
    private final List<CompletedCall> completedCalls = new CopyOnWriteArrayList<>();

    @Override
    public StartedObservation start(
            RequestMetadata requestMetadata,
            ObservedOperation operation) {
        startedCalls.add(new StartedCall(requestMetadata, operation));
        return outcome -> completedCalls.add(new CompletedCall(requestMetadata, operation, outcome));
    }

    /**
     * Returns an immutable snapshot of started observations.
     *
     * @return started observations
     */
    public List<StartedCall> startedCalls() {
        return List.copyOf(startedCalls);
    }

    /**
     * Returns an immutable snapshot of completed observations.
     *
     * @return completed observations
     */
    public List<CompletedCall> completedCalls() {
        return List.copyOf(completedCalls);
    }

    /**
     * Captures one subscription-scoped observation start.
     */
    public record StartedCall(RequestMetadata requestMetadata, ObservedOperation operation) {
    }

    /**
     * Captures one terminal observation with no operation payload or failure details.
     */
    public record CompletedCall(
            RequestMetadata requestMetadata,
            ObservedOperation operation,
            OperationOutcome outcome) {
    }
}
