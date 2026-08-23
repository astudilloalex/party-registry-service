package com.alexastudillo.partyregistry.infrastructure.integration.geographic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public final class GeographicReferenceStubResource implements QuarkusTestResourceLifecycleManager {

    public static final String REST_CLIENT_CONFIG_KEY = "geographic-reference";
    public static final String REST_CLIENT_URL_PROPERTY = "quarkus.rest-client." + REST_CLIENT_CONFIG_KEY + ".url";

    private static final String COUNTRY_PATH = "/api/v1/countries/by-alpha2/";
    private static final String FIXTURE_ROOT = "geographic/";
    private static final Pattern FIXTURE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Object LIFECYCLE_LOCK = new Object();

    private static volatile WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        synchronized (LIFECYCLE_LOCK) {
            if (wireMockServer != null) {
                throw new IllegalStateException("Geographic Reference stub is already running");
            }

            WireMockServer server = new WireMockServer(wireMockConfig()
                    .bindAddress("127.0.0.1")
                    .dynamicPort()
                    .asynchronousResponseEnabled(true)
                    .asynchronousResponseThreads(16));
            server.start();
            wireMockServer = server;
            return Map.of(REST_CLIENT_URL_PROPERTY, server.baseUrl());
        }
    }

    @Override
    public void stop() {
        synchronized (LIFECYCLE_LOCK) {
            if (wireMockServer != null) {
                wireMockServer.stop();
                wireMockServer = null;
            }
        }
    }

    public static String baseUrl() {
        return server().baseUrl();
    }

    public static void stubCountry(String alpha2Code, String fixtureName) {
        server().stubFor(get(urlPathEqualTo(countryPath(alpha2Code)))
                .willReturn(responseFrom(fixtureName)));
    }

    public static void stubCancellableCountry(String alpha2Code) {
        stubCountry(alpha2Code, "cancellation");
    }

    public static int requestCount() {
        return server().getAllServeEvents().size();
    }

    public static int requestCount(String alpha2Code) {
        return requestsFor(alpha2Code).size();
    }

    public static int requestCount(RequestPatternBuilder requestPattern) {
        return server().findAll(Objects.requireNonNull(requestPattern, "requestPattern must not be null")).size();
    }

    public static List<LoggedRequest> requestsFor(String alpha2Code) {
        return List.copyOf(server().findAll(getRequestedFor(urlPathEqualTo(countryPath(alpha2Code)))));
    }

    public static void verifyRequest(int expectedCount, String alpha2Code) {
        verifyRequest(expectedCount, getRequestedFor(urlPathEqualTo(countryPath(alpha2Code))));
    }

    public static void verifyTrustedRequest(
            int expectedCount,
            String alpha2Code,
            String tenantId,
            String userId,
            String processId) {
        verifyRequest(expectedCount, getRequestedFor(urlPathEqualTo(countryPath(alpha2Code)))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Tenant-Id", equalTo(tenantId))
                .withHeader("User-Id", equalTo(userId))
                .withHeader("Process-Id", equalTo(processId)));
    }

    public static void verifyRequest(int expectedCount, RequestPatternBuilder requestPattern) {
        server().verify(expectedCount, Objects.requireNonNull(requestPattern, "requestPattern must not be null"));
    }

    public static void resetRequests() {
        server().resetRequests();
    }

    public static void reset() {
        server().resetAll();
    }

    private static ResponseDefinitionBuilder responseFrom(String fixtureName) {
        JsonNode descriptor = loadFixture(fixtureName);
        if (!descriptor.isObject()) {
            throw new IllegalArgumentException("Geographic fixture must contain a JSON object: " + fixtureName);
        }

        ResponseDefinitionBuilder response = aResponse();
        JsonNode httpStatus = descriptor.get("httpStatus");
        if (httpStatus != null) {
            response.withStatus(requiredInt(httpStatus, "httpStatus", fixtureName));
        }

        JsonNode contentType = descriptor.get("contentType");
        if (contentType != null) {
            response.withHeader("Content-Type", requiredText(contentType, "contentType", fixtureName));
        }

        JsonNode responseHeaders = descriptor.get("responseHeaders");
        if (responseHeaders != null) {
            if (!responseHeaders.isObject()) {
                throw new IllegalArgumentException("responseHeaders must be an object in fixture: " + fixtureName);
            }
            responseHeaders.properties().forEach(header -> response.withHeader(
                    header.getKey(),
                    headerValues(header.getValue(), header.getKey(), fixtureName)));
        }

        JsonNode body = descriptor.get("body");
        if (body != null) {
            response.withBody(body.isTextual() ? body.textValue() : writeJson(body, fixtureName));
        }

        JsonNode delay = descriptor.get("delayMilliseconds");
        if (delay != null) {
            response.withFixedDelay(requiredInt(delay, "delayMilliseconds", fixtureName));
        }

        JsonNode fault = descriptor.get("fault");
        if (fault != null) {
            String faultName = requiredText(fault, "fault", fixtureName);
            try {
                response.withFault(Fault.valueOf(faultName));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown WireMock fault in fixture " + fixtureName + ": " + faultName,
                        exception);
            }
        }
        return response;
    }

    private static JsonNode loadFixture(String fixtureName) {
        String name = Objects.requireNonNull(fixtureName, "fixtureName must not be null");
        if (!FIXTURE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid geographic fixture name: " + name);
        }

        String resourceName = FIXTURE_ROOT + name + ".json";
        try (InputStream input = GeographicReferenceStubResource.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalArgumentException("Geographic fixture not found: " + name);
            }
            return OBJECT_MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read geographic fixture: " + name, exception);
        }
    }

    private static String[] headerValues(JsonNode value, String headerName, String fixtureName) {
        if (value.isTextual()) {
            return new String[] { value.textValue() };
        }
        if (value.isArray() && !value.isEmpty()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(requiredText(item, "responseHeaders." + headerName, fixtureName)));
            return values.toArray(String[]::new);
        }
        throw new IllegalArgumentException(
                "Response header values must be a string or non-empty string array in fixture: " + fixtureName);
    }

    private static String requiredText(JsonNode value, String property, String fixtureName) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(property + " must be a non-blank string in fixture: " + fixtureName);
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode value, String property, String fixtureName) {
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException(property + " must be an integer in fixture: " + fixtureName);
        }
        int result = value.intValue();
        if (result < 0) {
            throw new IllegalArgumentException(property + " must not be negative in fixture: " + fixtureName);
        }
        return result;
    }

    private static String writeJson(JsonNode body, String fixtureName) {
        try {
            return OBJECT_MAPPER.writeValueAsString(body);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot serialize body from geographic fixture: " + fixtureName, exception);
        }
    }

    private static String countryPath(String alpha2Code) {
        String code = Objects.requireNonNull(alpha2Code, "alpha2Code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("alpha2Code must not be blank");
        }
        return COUNTRY_PATH + code;
    }

    private static WireMockServer server() {
        WireMockServer server = wireMockServer;
        if (server == null || !server.isRunning()) {
            throw new IllegalStateException("Geographic Reference stub is not running");
        }
        return server;
    }
}
