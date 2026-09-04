package com.alexastudillo.partyregistry.api.context;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationOutcome;
import jakarta.enterprise.context.RequestScoped;

import java.util.Objects;

/**
 * Retains validated request metadata and filter lifecycle state for one HTTP
 * request.
 */
@RequestScoped
public class RequestMetadataContext {

    private RequestMetadata metadata;
    private String acceptedProcessId;
    private String method;
    private String path;
    private long startedAtNanos;
    private boolean mdcInitialized;
    private IdempotentCreationOutcome idempotencyOutcome;

    /**
     * Starts completion tracking before request validation occurs.
     *
     * @param method HTTP method
     * @param path   request path
     */
    public void start(String method, String path) {
        this.method = Objects.requireNonNull(method, "method");
        this.path = Objects.requireNonNull(path, "path");
        this.startedAtNanos = System.nanoTime();
    }

    /**
     * Records the process identifier only after canonical validation succeeds.
     *
     * @param processId unchanged accepted header value
     */
    public void acceptProcessId(String processId) {
        this.acceptedProcessId = Objects.requireNonNull(processId, "processId");
    }

    /**
     * Stores the complete trusted context after all required headers pass
     * validation.
     *
     * @param metadata validated application request metadata
     */
    public void initialize(RequestMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * Returns the trusted metadata required by API resources.
     *
     * @return validated request metadata
     * @throws IllegalStateException when the filter did not initialize the context
     */
    public RequestMetadata metadata() {
        if (metadata == null) {
            throw new IllegalStateException("Request metadata is not initialized");
        }
        return metadata;
    }

    public String acceptedProcessId() {
        return acceptedProcessId;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public long startedAtNanos() {
        return startedAtNanos;
    }

    public boolean isMdcInitialized() {
        return mdcInitialized;
    }

    public void markMdcInitialized() {
        this.mdcInitialized = true;
    }

    /**
     * Records whether a successful create was original or replayed.
     *
     * @param outcome idempotent creation outcome
     */
    public void recordIdempotencyOutcome(IdempotentCreationOutcome outcome) {
        this.idempotencyOutcome = Objects.requireNonNull(outcome, "outcome");
    }

    public IdempotentCreationOutcome idempotencyOutcome() {
        return idempotencyOutcome;
    }
}
