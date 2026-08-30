package com.alexastudillo.partyregistry.infrastructure.integration.geographic;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hosts controlled Geographic Reference responses for reactive adapter tests.
 */
public final class GeographicReferenceStubResource implements QuarkusTestResourceLifecycleManager {

    static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    static final String USER_ID = "geographic-reference-adapter-test";
    static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";

    private static final String BASE_PATH = "/api/v1/countries/by-alpha2/";
    private static final long DELAYED_RESPONSE_MILLIS = 500;
    private static final Map<String, AtomicInteger> REQUEST_COUNTS = new ConcurrentHashMap<>();
    private static final String SUCCESS_RESPONSE = """
            {
              "status": 200,
              "code": "successful",
              "data": {
                "id": "00000000-0000-7000-8000-000000000218",
                "alpha2Code": "%s",
                "alpha3Code": "ECU",
                "numericCode": "218",
                "defaultName": "Ecuador",
                "officialName": "Republic of Ecuador",
                "independent": true,
                "status": "ACTIVE",
                "validFrom": "1830-05-13",
                "validUntil": null,
                "sourceAuthority": "ISO",
                "sourceReference": "ISO 3166-1",
                "sourceRevision": "2025",
                "createdAt": "2026-01-01T00:00:00Z",
                "createdBy": "test-fixture",
                "updatedAt": "2026-01-02T00:00:00Z",
                "updatedBy": "test-fixture",
                "version": 2
              }
            }
            """;

    private HttpServer server;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;

    @Override
    public Map<String, String> start() {
        try {
            REQUEST_COUNTS.clear();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("geographic-reference-delay-", 0).factory());
            server.setExecutor(executor);
            server.createContext(BASE_PATH, this::handle);
            server.start();
            return Map.of(
                    "quarkus.rest-client.geographic-reference.url",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "quarkus.rest-client.geographic-reference.connect-timeout", "100",
                    "quarkus.rest-client.geographic-reference.read-timeout", "100");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start Geographic Reference test server", exception);
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.close();
        }
    }

    static int requestCount(String alpha2Code) {
        AtomicInteger count = REQUEST_COUNTS.get(alpha2Code);
        return count == null ? 0 : count.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equals(exchange.getRequestMethod()) || !path.startsWith(BASE_PATH)) {
            send(exchange, 404, "{\"status\":404,\"code\":\"not-found\"}", true);
            return;
        }

        String alpha2Code = path.substring(BASE_PATH.length());
        REQUEST_COUNTS.computeIfAbsent(alpha2Code, ignored -> new AtomicInteger()).incrementAndGet();
        if (!hasTrustedHeaders(exchange)) {
            send(exchange, 400, "{\"status\":400,\"code\":\"bad-request\"}", true);
            return;
        }

        switch (alpha2Code) {
            case "EC" -> send(exchange, 200, SUCCESS_RESPONSE.formatted(alpha2Code), true);
            case "ZZ" -> send(exchange, 404, "{\"status\":404,\"code\":\"country-not-found\"}", true);
            case "SE" -> send(exchange, 503, "{\"status\":503,\"code\":\"server-error\"}", true);
            case "MJ" -> send(exchange, 200, "{", true);
            case "MD" -> send(exchange, 200, "{\"status\":200,\"code\":\"successful\"}", true);
            case "RN" -> send(
                    exchange,
                    200,
                    SUCCESS_RESPONSE.formatted(alpha2Code).replace("\"validFrom\": \"1830-05-13\",", ""),
                    true);
            case "NF" -> send(
                    exchange,
                    404,
                    "{\"status\":404,\"code\":\"country-not-found\",\"data\":null}",
                    true);
            case "CT" -> send(exchange, 200, SUCCESS_RESPONSE.formatted(alpha2Code), true, "text/plain");
            case "ST" -> send(
                    exchange,
                    404,
                    "{\"status\":\"404\",\"code\":\"country-not-found\"}",
                    true);
            case "SB" -> send(
                    exchange,
                    200,
                    SUCCESS_RESPONSE.formatted(alpha2Code).replace("\"independent\": true",
                            "\"independent\": \"true\""),
                    true);
            case "UI" -> send(
                    exchange,
                    200,
                    SUCCESS_RESPONSE.formatted(alpha2Code).replace(
                            "00000000-0000-7000-8000-000000000218",
                            "AAAAAAAAcACAAAAAAAAA2A=="),
                    true);
            case "CF" -> exchange.close();
            case "TO" -> delayedSuccess(exchange, alpha2Code);
            default -> send(exchange, 404, "{\"status\":404,\"code\":\"country-not-found\"}", true);
        }
    }

    private void delayedSuccess(HttpExchange exchange, String alpha2Code) {
        scheduler.schedule(() -> {
            try {
                send(exchange, 200, SUCCESS_RESPONSE.formatted(alpha2Code), true);
            } catch (IOException _) {
                // The client timeout normally closes the connection before this delayed
                // response.
                exchange.close();
            }
        }, DELAYED_RESPONSE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private boolean hasTrustedHeaders(HttpExchange exchange) {
        return List.of(TENANT_ID).equals(exchange.getRequestHeaders().get("Tenant-Id"))
                && List.of(USER_ID).equals(exchange.getRequestHeaders().get("User-Id"))
                && List.of(PROCESS_ID).equals(exchange.getRequestHeaders().get("Process-Id"));
    }

    private void send(HttpExchange exchange, int status, String body, boolean echoProcessId) throws IOException {
        send(exchange, status, body, echoProcessId, "application/json");
    }

    private void send(
            HttpExchange exchange,
            int status,
            String body,
            boolean echoProcessId,
            String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (echoProcessId) {
            exchange.getResponseHeaders().set("Process-Id", PROCESS_ID);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
