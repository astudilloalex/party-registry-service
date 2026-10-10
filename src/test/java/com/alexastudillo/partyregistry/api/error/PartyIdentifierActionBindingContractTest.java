package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies actual optional reason binding and buffered zero/nonzero/chunked byte guard on the event loop. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class PartyIdentifierActionBindingContractTest {
    private static final String ROOT = "/v1/identifier-action-binding-verification";
    private static final String ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    @Inject ObjectMapper json;

    @Test
    void absenceAndEmptyReasonsAreDistinctFromLiteralNullAndExplicitNullProperties() {
        for (String body : List.of("", "{}")) {
            var response = request().body(body).post(ROOT + "/reason/" + ID);
            assertEquals(200, response.statusCode()); assertEquals(ID, response.header("Process-Id"));
            assertTrue(response.jsonPath().getBoolean("data.eventLoop")); assertFalse(response.jsonPath().getBoolean("data.codePresent"));
        }
        for (String body : List.of("null", " \r\n\t", "{\"reasonCode\":null}", "{\"reasonDetail\":null}", "{\"unknown\":null}",
                "{\"reasonCode\":42}", "{\"reasonCode\":\"a\",\"reasonCode\":\"a\"}", "{} {}")) { error("/reason/bad-party", body); }
    }

    @Test
    void exactControlEmptyAndSupplementaryStringsAreAcceptedWithCodePointBounds() throws Exception {
        String body = json.writeValueAsString(Map.of("reasonCode", "😀".repeat(64), "reasonDetail", "😀".repeat(1000)));
        var response = request().body(body).post(ROOT + "/reason/" + ID);
        assertEquals(200, response.statusCode()); assertEquals(64, response.jsonPath().getInt("data.codePoints")); assertEquals(1000, response.jsonPath().getInt("data.detailPoints"));
        String controls = json.writeValueAsString(Map.of("reasonCode", "", "reasonDetail", "\u0000\n\t"));
        response = request().body(controls).post(ROOT + "/reason/" + ID);
        assertEquals(200, response.statusCode()); assertTrue(response.jsonPath().getBoolean("data.codePresent")); assertEquals(3, response.jsonPath().getInt("data.detailPoints"));
        error("/reason/bad-party", json.writeValueAsString(Map.of("reasonCode", "😀".repeat(65))));
        error("/reason/bad-party", json.writeValueAsString(Map.of("reasonDetail", "😀".repeat(1001))));
    }

    @Test
    void everyNonzeroBodyPrecedesSelectorsAndZeroBytesStayNonblocking() {
        for (String body : List.of("{}", "null", " \r\n\t")) { error("/verify/bad-party", body); }
        var accepted = request().body(new byte[0]).post(ROOT + "/verify/" + ID);
        assertEquals(200, accepted.statusCode()); assertTrue(accepted.jsonPath().getBoolean("data.eventLoop"));
        var missing = request().post(ROOT + "/verify/bad-party");
        assertEquals(400, missing.statusCode()); assertEquals(Map.of("status", 400, "code", "party-id-invalid"), missing.jsonPath().getMap("$"));
    }

    @Test
    void unknownLengthChunkedBodiesRejectContentAndAcceptActualZeroBytes() throws Exception {
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()) {
            for (String body : List.of("{}", " \r\n\t", "")) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
                assertEquals(-1, publisher.contentLength());
                String target = body.isEmpty() ? ID : "bad-party";
                var request = HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ':' + RestAssured.port + ROOT + "/verify/" + target))
                        .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json").header("Process-Id", ID)
                        .header("Tenant-Id", ID).header("User-Id", "action-body-tester").POST(publisher).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(body.isEmpty() ? 200 : 400, response.statusCode()); assertEquals(ID, response.headers().firstValue("Process-Id").orElseThrow());
                var parsed = json.readTree(response.body());
                if (body.isEmpty()) { assertTrue(parsed.path("data").path("eventLoop").booleanValue()); }
                else { assertEquals(json.valueToTree(Map.of("status", 400, "code", "bad-request")), parsed); }
            }
        }
    }

    private static void error(String path, String body) {
        var response = request().body(body).post(ROOT + path);
        assertEquals(400, response.statusCode()); assertEquals(ID, response.header("Process-Id"));
        assertEquals(Map.of("status", 400, "code", "bad-request"), response.jsonPath().getMap("$"));
    }
    private static RequestSpecification request() { return given().contentType("application/json").header("Process-Id", ID).header("Tenant-Id", ID).header("User-Id", "action-body-tester"); }
}
