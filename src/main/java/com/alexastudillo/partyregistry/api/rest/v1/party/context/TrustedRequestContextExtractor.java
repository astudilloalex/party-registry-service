package com.alexastudillo.partyregistry.api.rest.v1.party.context;

import java.util.List;
import java.util.UUID;

import com.alexastudillo.partyregistry.application.party.command.TrustedCreationContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class TrustedRequestContextExtractor {

    public static final String TENANT_ID_HEADER = "Tenant-Id";
    public static final String USER_ID_HEADER = "User-Id";
    public static final String PROCESS_ID_HEADER = "Process-Id";

    public TrustedCreationContext extract(HttpHeaders headers) {
        String tenantHeaderValue = singleValue(headers, TENANT_ID_HEADER);
        UUID tenantId = parseUuid(tenantHeaderValue, TENANT_ID_HEADER, false);
        String userId = singleValue(headers, USER_ID_HEADER);
        if (userId.isBlank() || userId.length() > 128) {
            throw new InvalidRequestContextException(USER_ID_HEADER);
        }
        String processHeaderValue = singleValue(headers, PROCESS_ID_HEADER);
        UUID processId = parseUuid(processHeaderValue, PROCESS_ID_HEADER, true);
        return new TrustedCreationContext(
                tenantId,
                tenantHeaderValue,
                userId,
                processId,
                processHeaderValue);
    }

    private static String singleValue(HttpHeaders headers, String headerName) {
        if (headers == null) {
            throw new InvalidRequestContextException(headerName);
        }
        List<String> values = headers.getRequestHeader(headerName);
        if (values == null || values.size() != 1 || values.getFirst() == null) {
            throw new InvalidRequestContextException(headerName);
        }
        return values.getFirst();
    }

    private static UUID parseUuid(String value, String headerName, boolean requireCanonicalText) {
        try {
            UUID parsed = UUID.fromString(value);
            boolean canonical = requireCanonicalText
                    ? parsed.toString().equals(value)
                    : parsed.toString().equalsIgnoreCase(value);
            if (!canonical) {
                throw new InvalidRequestContextException(headerName);
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestContextException(headerName);
        }
    }

    public static final class InvalidRequestContextException extends RuntimeException {

        private final String headerName;

        public InvalidRequestContextException(String headerName) {
            super("A required context header is missing or invalid");
            this.headerName = headerName;
        }

        public String headerName() {
            return headerName;
        }
    }
}
