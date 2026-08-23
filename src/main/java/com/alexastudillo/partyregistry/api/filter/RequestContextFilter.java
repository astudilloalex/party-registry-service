package com.alexastudillo.partyregistry.api.filter;

import com.alexastudillo.partyregistry.api.response.ApiResponseCode;
import com.alexastudillo.partyregistry.api.response.ResponseManager;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates trusted headers, owns their MDC lifecycle, logs completion, and echoes Process-Id.
 */
@Priority(Priorities.AUTHENTICATION)
public class RequestContextFilter {

    public static final String TENANT_ID_HEADER = "Tenant-Id";
    public static final String USER_ID_HEADER = "User-Id";
    public static final String PROCESS_ID_HEADER = "Process-Id";

    private static final String TENANT_ID_MDC_KEY = "tenantId";
    private static final String USER_ID_MDC_KEY = "userId";
    private static final String PROCESS_ID_MDC_KEY = "processId";
    private static final String START_NANOS_PROPERTY = RequestContextFilter.class.getName() + ".startNanos";
    private static final String PROCESS_ID_PROPERTY = RequestContextFilter.class.getName() + ".processId";
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Logger LOG = Logger.getLogger(RequestContextFilter.class);

    private final ResponseManager responseManager;

    @Inject
    public RequestContextFilter(ResponseManager responseManager) {
        this.responseManager = responseManager;
    }

    /**
     * Validates exactly one value for each trusted context header before route matching.
     *
     * @param requestContext current request context
     * @return a standard bad-request response when validation fails, otherwise {@code null}
     */
    @ServerRequestFilter(preMatching = true)
    public Response validate(ContainerRequestContext requestContext) {
        if (isManagementPath(requestContext)) {
            return null;
        }

        requestContext.setProperty(START_NANOS_PROPERTY, System.nanoTime());

        String tenantId = singleHeader(requestContext, TENANT_ID_HEADER);
        String userId = singleHeader(requestContext, USER_ID_HEADER);
        String processId = singleHeader(requestContext, PROCESS_ID_HEADER);

        boolean tenantIdValid = isCanonicalUuid(tenantId);
        boolean userIdValid = isSafeUserId(userId);
        boolean processIdValid = isCanonicalUuid(processId);

        if (tenantIdValid) {
            MDC.put(TENANT_ID_MDC_KEY, tenantId);
        }
        if (userIdValid) {
            MDC.put(USER_ID_MDC_KEY, userId);
        }
        if (processIdValid) {
            requestContext.setProperty(PROCESS_ID_PROPERTY, processId);
            MDC.put(PROCESS_ID_MDC_KEY, processId);
        }

        if (!tenantIdValid || !userIdValid || !processIdValid) {
            return responseManager.error(
                    Response.Status.BAD_REQUEST,
                    ApiResponseCode.BAD_REQUEST);
        }
        return null;
    }

    /**
     * Completes the trusted context lifecycle after the response has been mapped.
     *
     * @param requestContext current request context
     * @param responseContext current response context
     */
    @ServerResponseFilter
    public void complete(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext) {
        if (isManagementPath(requestContext)) {
            return;
        }

        try {
            Object processId = requestContext.getProperty(PROCESS_ID_PROPERTY);
            if (processId instanceof String acceptedProcessId) {
                responseContext.getHeaders().putSingle(PROCESS_ID_HEADER, acceptedProcessId);
            } else {
                responseContext.getHeaders().remove(PROCESS_ID_HEADER);
            }

            long elapsedMillis = elapsedMillis(requestContext.getProperty(START_NANOS_PROPERTY));
            LOG.infof(
                    "Request completed method=%s path=/%s status=%d durationMs=%d",
                    requestContext.getMethod(),
                    requestPath(requestContext),
                    responseContext.getStatus(),
                    elapsedMillis);
        } finally {
            MDC.remove(PROCESS_ID_MDC_KEY);
            MDC.remove(USER_ID_MDC_KEY);
            MDC.remove(TENANT_ID_MDC_KEY);
        }
    }

    private static String singleHeader(ContainerRequestContext requestContext, String headerName) {
        List<String> values = requestContext.getHeaders().get(headerName);
        if (values == null || values.size() != 1) {
            return null;
        }
        return values.getFirst();
    }

    private static boolean isCanonicalUuid(String value) {
        return value != null && CANONICAL_UUID.matcher(value).matches();
    }

    private static boolean isSafeUserId(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 128) {
            return false;
        }
        return value.codePoints().noneMatch(RequestContextFilter::isUnsafeCodePoint);
    }

    private static boolean isUnsafeCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SURROGATE;
    }

    private static boolean isManagementPath(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath().replaceFirst("^/", "");
        return path.equals("q") || path.startsWith("q/");
    }

    private static String requestPath(ContainerRequestContext requestContext) {
        return requestContext.getUriInfo().getPath().replaceFirst("^/", "");
    }

    private static long elapsedMillis(Object startNanos) {
        if (!(startNanos instanceof Long start)) {
            return 0;
        }
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }
}
