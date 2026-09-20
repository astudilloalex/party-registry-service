package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.api.context.RequestMetadataContext;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.observability.PartyHttpObservability;
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
    private static final int MAX_USER_LENGTH = 128;

    private final RequestMetadataContext metadataContext;
    private final PartyHttpObservability observability;

    @Inject
    public RequestContextFilter(
            RequestMetadataContext metadataContext,
            PartyHttpObservability observability) {
        this.metadataContext = metadataContext;
        this.observability = observability;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalizedPath(requestContext);
        if (isManagementPath(path)) {
            return;
        }

        clearOwnedMdc();
        metadataContext.start(requestContext.getMethod(), path);
        MultivaluedMap<String, String> headers = requestContext.getHeaders();

        String processIdValue = requireSingle(headers, PROCESS_ID_HEADER,
                PartyResponseCode.PROCESS_ID_REQUIRED, PartyResponseCode.PROCESS_ID_DUPLICATED);
        UUID processId = parseCanonicalUuid(processIdValue, PROCESS_ID_HEADER, PartyResponseCode.PROCESS_ID_INVALID);
        metadataContext.acceptProcessId(processIdValue);
        // Retain only validated correlation context when a later header fails; the response filter owns cleanup.
        MDC.put(PROCESS_ID_MDC, processIdValue);
        metadataContext.markMdcInitialized();

        String tenantIdValue = requireSingle(headers, TENANT_ID_HEADER,
                PartyResponseCode.TENANT_ID_REQUIRED, PartyResponseCode.TENANT_ID_DUPLICATED);
        UUID tenantId = parseCanonicalUuid(tenantIdValue, TENANT_ID_HEADER, PartyResponseCode.TENANT_ID_INVALID);
        MDC.put(TENANT_ID_MDC, tenantIdValue);
        String userId = requireSingle(headers, USER_ID_HEADER,
                PartyResponseCode.USER_ID_REQUIRED, PartyResponseCode.USER_ID_DUPLICATED);
        validateUserId(userId);

        metadataContext.initialize(new RequestMetadata(new TenantId(tenantId), userId, processId));
        MDC.put(USER_ID_MDC, userId);
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
        observability.recordMutationCompletion(operation, responseContext.getStatus(), code, metadataContext.mutationDisposition());
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

    private static String requireSingle(
            MultivaluedMap<String, String> headers,
            String name,
            PartyResponseCode requiredCode,
            PartyResponseCode duplicatedCode) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            throw reject(name, requiredCode);
        }
        if (values.size() > 1) {
            throw reject(name, duplicatedCode);
        }
        if (values.getFirst() == null) {
            throw reject(name, requiredCode);
        }
        return values.getFirst();
    }

    private static UUID parseCanonicalUuid(String value, String header, PartyResponseCode invalidCode) {
        UUID parsed;
        try {
            parsed = UUID.fromString(value);
        } catch (IllegalArgumentException _) {
            throw reject(header, invalidCode);
        }
        if (!parsed.toString().equals(value)) {
            throw reject(header, invalidCode);
        }
        return parsed;
    }

    private static void validateUserId(String userId) {
        if (userId.isBlank()) {
            throw reject(USER_ID_HEADER, PartyResponseCode.USER_ID_BLANK);
        }
        if (userId.codePointCount(0, userId.length()) > MAX_USER_LENGTH) {
            throw reject(USER_ID_HEADER, PartyResponseCode.USER_ID_TOO_LONG);
        }
        if (userId.chars().anyMatch(Character::isISOControl)) {
            throw reject(USER_ID_HEADER, PartyResponseCode.USER_ID_UNSAFE);
        }
    }

    private static ApiResponseException reject(String header, PartyResponseCode code) {
        LOGGER.log(System.Logger.Level.WARNING,
                "Request rejected status=400 code={0} source=request-context header={1} rule={0}",
                code.getCode(), header);
        return new ApiResponseException(code);
    }

    static void clearOwnedMdc() {
        MDC.remove(PROCESS_ID_MDC);
        MDC.remove(USER_ID_MDC);
        MDC.remove(TENANT_ID_MDC);
    }
}
