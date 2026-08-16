package com.alexastudillo.partyregistry.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.fail;

class RestAdapterAcceptanceTest {
    private static final URI ADAPTER_URI = URI.create(
            System.getProperty("party.registry.contract.base-uri", "http://127.0.0.1:8081/api/v1"));

    @Test
    void supportedOperationWithoutAuthenticationUsesContractEnvelopeAndGeneratedProcessId() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(ADAPTER_URI.resolve("/api/v1/parties"))
                .timeout(Duration.ofSeconds(2))
                .header("tenant-id", "018f0c72-4a7b-7c91-8b2a-1234567890aa")
                .header("user-id", "synthetic-contract-user")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException exception) {
            fail("Reactive REST adapter is absent: no Party Registry endpoint accepted the contract request at "
                    + ADAPTER_URI, exception);
            return;
        } catch (IOException exception) {
            fail("Reactive REST adapter did not provide a response at " + ADAPTER_URI, exception);
            return;
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            fail("V1 must not apply authentication or authorization: HTTP " + response.statusCode());
        }
        if (response.statusCode() != 200) {
            fail("Expected HTTP 200 from tenant-scoped Party search but got " + response.statusCode());
        }
        if (response.headers().firstValue("process-id").isEmpty()) {
            fail("Party search response omitted generated process-id");
        }
        JsonEnvelopeAssertions.assertSuccessPage(response.body());
    }
}
