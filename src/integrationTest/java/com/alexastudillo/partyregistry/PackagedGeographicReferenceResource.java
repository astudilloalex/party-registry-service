package com.alexastudillo.partyregistry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts deterministic Geographic Reference responses for packaged smoke tests.
 */
public final class PackagedGeographicReferenceResource implements QuarkusTestResourceLifecycleManager {

    private static final String BASE_PATH = "/api/v1/countries/by-alpha2/";
    private static final String SUCCESS_RESPONSE = """
            {
              "status": 200,
              "code": "successful",
              "data": {
                "id": "00000000-0000-7000-8000-000000000218",
                "alpha2Code": "EC",
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
                "createdBy": "packaged-fixture",
                "updatedAt": "2026-01-02T00:00:00Z",
                "updatedBy": "packaged-fixture",
                "version": 1
              }
            }
            """;

    private HttpServer server;
    private ExecutorService executor;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext(BASE_PATH, this::handle);
            server.start();
            return Map.of(
                    "quarkus.rest-client.geographic-reference.url",
                    "http://127.0.0.1:" + server.getAddress().getPort());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start packaged Geographic Reference server", exception);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.close();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equals(exchange.getRequestMethod()) || !path.startsWith(BASE_PATH)) {
            send(exchange, 404, "{\"status\":404,\"code\":\"country-not-found\"}");
            return;
        }

        String processId = exchange.getRequestHeaders().getFirst("Process-Id");
        if (processId != null) {
            exchange.getResponseHeaders().set("Process-Id", processId);
        }
        String alpha2Code = path.substring(BASE_PATH.length());
        if ("EC".equals(alpha2Code)) {
            send(exchange, 200, SUCCESS_RESPONSE);
        } else if ("SE".equals(alpha2Code)) {
            send(exchange, 503, "{\"status\":503,\"code\":\"server-error\"}");
        } else {
            send(exchange, 404, "{\"status\":404,\"code\":\"country-not-found\"}");
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
