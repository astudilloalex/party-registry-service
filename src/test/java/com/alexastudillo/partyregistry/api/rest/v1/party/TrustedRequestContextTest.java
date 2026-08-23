package com.alexastudillo.partyregistry.api.rest.v1.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.alexastudillo.partyregistry.api.rest.v1.party.PartyResource;
import com.alexastudillo.partyregistry.api.rest.v1.party.context.TrustedRequestContextExtractor;
import com.alexastudillo.partyregistry.api.rest.v1.party.mapper.PartyRequestMapper;
import com.alexastudillo.partyregistry.application.party.command.CreatePartyCommand;
import com.alexastudillo.partyregistry.application.party.command.TrustedCreationContext;
import com.alexastudillo.partyregistry.application.party.port.in.CreatePartyUseCase;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

class TrustedRequestContextTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String TENANT_HEADER_VALUE = TENANT_ID.toUpperCase(Locale.ROOT);
    private static final String USER_ID = "service-account:party-registration";
    private static final String PROCESS_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2";

    private final TrustedRequestContextExtractor extractor = new TrustedRequestContextExtractor();

    @Test
    void extractsExactlyOneTrustedHeaderAndPreservesCanonicalProcessId() {
        TrustedCreationContext context = extractor.extract(headers(TENANT_HEADER_VALUE, USER_ID, PROCESS_ID));

        assertEquals(TENANT_ID, context.tenantId().toString());
        assertEquals(TENANT_HEADER_VALUE, context.tenantHeaderValue());
        assertEquals(USER_ID, context.userId());
        assertEquals(PROCESS_ID, context.processId().toString());
        assertEquals(PROCESS_ID, context.processHeaderValue());
    }

    @Test
    void rejectsInvalidContextBeforeCallingAnyPort() throws Exception {
        List<HttpHeaders> invalidHeaders = List.of(
                headers(null, USER_ID, PROCESS_ID),
                headers(TENANT_ID, null, PROCESS_ID),
                headers(TENANT_ID, USER_ID, null),
                headers("not-a-uuid", USER_ID, PROCESS_ID),
                headers(TENANT_ID, USER_ID, "not-a-uuid"),
                headers(TENANT_ID, USER_ID, PROCESS_ID.toUpperCase(Locale.ROOT)),
                headers(TENANT_ID, " ", PROCESS_ID),
                headers(TENANT_ID, "u".repeat(129), PROCESS_ID),
                headersWithDuplicate(TrustedRequestContextExtractor.TENANT_ID_HEADER, TENANT_ID),
                headersWithDuplicate(TrustedRequestContextExtractor.USER_ID_HEADER, USER_ID),
                headersWithDuplicate(TrustedRequestContextExtractor.PROCESS_ID_HEADER, PROCESS_ID));
        RecordingCreatePartyUseCase useCase = new RecordingCreatePartyUseCase();
        PartyResource resource = new PartyResource(
                new PartyRequestMapper(new ObjectMapper()),
                extractor,
                useCase);
        JsonNode validRequest = new ObjectMapper().readTree("""
                {
                  "type": "NATURAL_PERSON",
                  "naturalPersonDetails": {
                    "givenNames": "Ana Maria",
                    "familyNames": "Example"
                  }
                }
                """);

        invalidHeaders.forEach(headers -> assertThrows(
                TrustedRequestContextExtractor.InvalidRequestContextException.class,
                () -> resource.createParty(validRequest, headers)));

        assertEquals(0, useCase.callCount());
    }

    private static HttpHeaders headers(String tenantId, String userId, String processId) {
        MultivaluedMap<String, String> values = new MultivaluedHashMap<>();
        addIfPresent(values, TrustedRequestContextExtractor.TENANT_ID_HEADER, tenantId);
        addIfPresent(values, TrustedRequestContextExtractor.USER_ID_HEADER, userId);
        addIfPresent(values, TrustedRequestContextExtractor.PROCESS_ID_HEADER, processId);
        return new TestHeaders(values);
    }

    private static HttpHeaders headersWithDuplicate(String headerName, String value) {
        MultivaluedMap<String, String> values = new MultivaluedHashMap<>();
        values.add(TrustedRequestContextExtractor.TENANT_ID_HEADER, TENANT_HEADER_VALUE);
        values.add(TrustedRequestContextExtractor.USER_ID_HEADER, USER_ID);
        values.add(TrustedRequestContextExtractor.PROCESS_ID_HEADER, PROCESS_ID);
        values.add(headerName, value);
        return new TestHeaders(values);
    }

    private static void addIfPresent(MultivaluedMap<String, String> values, String header, String value) {
        if (value != null) {
            values.add(header, value);
        }
    }

    private record TestHeaders(MultivaluedMap<String, String> values) implements HttpHeaders {

        @Override
        public List<String> getRequestHeader(String name) {
            return values.get(name);
        }

        @Override
        public String getHeaderString(String name) {
            return values.getFirst(name);
        }

        @Override
        public MultivaluedMap<String, String> getRequestHeaders() {
            return values;
        }

        @Override
        public List<MediaType> getAcceptableMediaTypes() {
            return List.of();
        }

        @Override
        public List<Locale> getAcceptableLanguages() {
            return List.of();
        }

        @Override
        public MediaType getMediaType() {
            return null;
        }

        @Override
        public Locale getLanguage() {
            return null;
        }

        @Override
        public Map<String, Cookie> getCookies() {
            return Map.of();
        }

        @Override
        public Date getDate() {
            return null;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    private static final class RecordingCreatePartyUseCase implements CreatePartyUseCase {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Uni<CreatePartyResult> create(CreatePartyCommand command, TrustedCreationContext context) {
            calls.incrementAndGet();
            return Uni.createFrom().failure(new UnsupportedOperationException());
        }

        int callCount() {
            return calls.get();
        }
    }
}
