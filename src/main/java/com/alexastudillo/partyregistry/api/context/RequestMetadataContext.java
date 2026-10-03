package com.alexastudillo.partyregistry.api.context;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import jakarta.enterprise.context.RequestScoped;

import java.util.Objects;

/**
 * Retains validated request metadata and filter lifecycle state for one HTTP
 * request.
 */
@RequestScoped
public class RequestMetadataContext {

    private static final String OUTCOME = "outcome";

    private RequestMetadata metadata;
    private String acceptedProcessId;
    private String method;
    private String path;
    private long startedAtNanos;
    private boolean mdcInitialized;
    private PartyRegistrationOutcome idempotencyOutcome;
    private PartyMutationOutcome.Disposition mutationDisposition;
    private NationalityMutationOutcome.Disposition nationalityDisposition;
    private boolean nationalityKeyed;
    private IdentifierSchemeMutationOutcome.Disposition identifierSchemeDisposition;

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
    public void recordIdempotencyOutcome(PartyRegistrationOutcome outcome) {
        this.idempotencyOutcome = Objects.requireNonNull(outcome, OUTCOME);
    }

    public PartyRegistrationOutcome idempotencyOutcome() {
        return idempotencyOutcome;
    }

    /** Retains only the committed mutation disposition for completion telemetry, never the historical result itself. */
    public void recordMutationDisposition(PartyMutationOutcome outcome) {
        this.mutationDisposition = Objects.requireNonNull(outcome, OUTCOME).disposition();
    }

    public PartyMutationOutcome.Disposition mutationDisposition() {
        return mutationDisposition;
    }

    /** Retains only a bounded keyed-attempt marker, never the replay key itself. */
    public void markNationalityKeyed() {
        nationalityKeyed = true;
    }

    /** Records the accepted nationality mutation disposition for request-completion telemetry. */
    public void recordNationalityDisposition(NationalityMutationOutcome outcome) {
        nationalityDisposition = Objects.requireNonNull(outcome, OUTCOME).disposition();
    }

    public NationalityMutationOutcome.Disposition nationalityDisposition() {
        return nationalityDisposition;
    }

    public boolean nationalityKeyed() {
        return nationalityKeyed;
    }

    /** Retains only committed scheme disposition for completion telemetry, never catalog data or replay keys. */
    public void recordIdentifierSchemeDisposition(IdentifierSchemeMutationOutcome outcome) {
        identifierSchemeDisposition = Objects.requireNonNull(outcome, OUTCOME).disposition();
    }

    public IdentifierSchemeMutationOutcome.Disposition identifierSchemeDisposition() {
        return identifierSchemeDisposition;
    }
}
