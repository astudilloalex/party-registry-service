package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.api.response.contract.CommonResponseCode;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.observability.NaturalPersonObservability;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates trusted HTTP context and owns its MDC and response lifecycle.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class RequestContextFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TENANT_ID_HEADER = "Tenant-Id";
    public static final String USER_ID_HEADER = "User-Id";
    public static final String PROCESS_ID_HEADER = "Process-Id";

    private static final System.Logger LOGGER = System.getLogger(RequestContextFilter.class.getName());
    private static final String PROCESS_ID_MDC = "processId";
    private static final String USER_ID_MDC = "userId";
    private static final String TENANT_ID_MDC = "tenantId";

    private final RequestMetadataContext metadataContext;
    private final NaturalPersonObservability observability;

    @Inject
    public RequestContextFilter(
            RequestMetadataContext metadataContext,
            NaturalPersonObservability observability) {
        this.metadataContext = metadataContext;
        this.observability = observability;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizedPath(requestContext);
        if (isManagementPath(path)) {
            return;
        }

        metadataContext.start(requestContext.getMethod(), path);
        try {
            MultivaluedMap<String, String> headers = requestContext.getHeaders();

            String processIdValue = requireSingle(headers, PROCESS_ID_HEADER);
            UUID processId = parseCanonicalUuid(processIdValue);
            metadataContext.acceptProcessId(processIdValue);

            String tenantIdValue = requireSingle(headers, TENANT_ID_HEADER);
            UUID tenantId = parseCanonicalUuid(tenantIdValue);
            String userId = requireSingle(headers, USER_ID_HEADER);

            RequestMetadata metadata = new RequestMetadata(new TenantId(tenantId), userId, processId);
            metadataContext.initialize(metadata);
            putMdc(metadata);
            metadataContext.markMdcInitialized();
        } catch (IllegalArgumentException exception) {
            throw new ApiResponseException(CommonResponseCode.BAD_REQUEST, exception);
        }
    }

    @Override
    public void filter(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext) throws IOException {
        String path = normalizedPath(requestContext);
        if (isManagementPath(path)) {
            return;
        }

        try {
            if (metadataContext.acceptedProcessId() != null) {
                responseContext.getHeaders().putSingle(
                        PROCESS_ID_HEADER,
                        metadataContext.acceptedProcessId());
            }
            logCompletion(responseContext);
        } finally {
            if (metadataContext.isMdcInitialized()) {
                clearOwnedMdc();
            }
        }
    }

    private void logCompletion(ContainerResponseContext responseContext) {
        long durationNanos = Math.max(0L, System.nanoTime() - metadataContext.startedAtNanos());
        long durationMillis = durationNanos / 1_000_000L;
        String code = responseContext.getEntity() instanceof ApiResponse<?> response
                ? response.getCode()
                : "unavailable";
        String operation = observability.operationName(metadataContext.method(), metadataContext.path());
        observability.recordCompletion(
                operation,
                responseContext.getStatus(),
                code,
                durationNanos,
                metadataContext.idempotencyOutcome());
        LOGGER.log(
                System.Logger.Level.INFO,
                "Request completed operation={0} status={1} code={2} durationMs={3}",
                operation,
                responseContext.getStatus(),
                code,
                durationMillis);
    }

    private static String normalizedPath(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean isManagementPath(String path) {
        return path.equals("/q") || path.startsWith("/q/");
    }

    private static String requireSingle(MultivaluedMap<String, String> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null) {
            throw new IllegalArgumentException(name + " must occur exactly once");
        }
        return values.getFirst();
    }

    private static UUID parseCanonicalUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("UUID header must use canonical lowercase form");
        }
        return parsed;
    }

    private static void putMdc(RequestMetadata metadata) {
        MDC.put(PROCESS_ID_MDC, metadata.processId().toString());
        MDC.put(USER_ID_MDC, metadata.userId());
        MDC.put(TENANT_ID_MDC, metadata.tenantId().value().toString());
    }

    static void clearOwnedMdc() {
        MDC.remove(PROCESS_ID_MDC);
        MDC.remove(USER_ID_MDC);
        MDC.remove(TENANT_ID_MDC);
    }
}
