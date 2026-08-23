package com.alexastudillo.partyregistry.api.rest.v1.party;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alexastudillo.partyregistry.api.rest.v1.party.error.PartyProblem;
import com.alexastudillo.partyregistry.application.party.command.CreatePartyCommand;
import com.alexastudillo.partyregistry.application.party.command.TrustedCreationContext;
import com.alexastudillo.partyregistry.application.party.port.in.CreatePartyUseCase;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(ServerOwnedFieldsResourceTest.NoPersistenceProfile.class)
class ServerOwnedFieldsResourceTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2";
    private static final String REJECTED_VALUE = "must-not-leak-7f4ca";

    @Inject
    RecordingCreatePartyUseCase useCase;

    @BeforeEach
    void resetUseCase() {
        useCase.reset();
    }

    @Test
    void rejectsServerOwnedAndUnknownRootFieldsBeforeCallingTheUseCase() {
        List<String> rejectedFields = List.of(
                "tenantId",
                "userId",
                "processId",
                "displayName",
                "recordStatus",
                "version",
                "createdAt",
                "createdBy",
                "updatedAt",
                "updatedBy",
                "unknown");

        rejectedFields.forEach(field -> given()
                .contentType("application/json")
                .accept(PartyProblem.MEDIA_TYPE)
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", "service-account:party-registration")
                .header("Process-Id", PROCESS_ID)
                .body(requestWith(field, REJECTED_VALUE))
                .when()
                .post("/internal/v1/parties")
                .then()
                .statusCode(422)
                .contentType(PartyProblem.MEDIA_TYPE)
                .body("code", equalTo("INVALID_PARTY_DATA"))
                .body("violations[0].location", equalTo("body"))
                .body("violations[0].path", equalTo("$." + field))
                .body(not(containsString(REJECTED_VALUE))));

        assertEquals(0, useCase.callCount());
    }

    @Test
    void rejectsUnknownNestedFieldsWithSanitizedJsonPath() {
        String request = """
                {
                  "type": "NATURAL_PERSON",
                  "naturalPersonDetails": {
                    "givenNames": "Ana Maria",
                    "familyNames": "Example",
                    "unknown": "%s"
                  }
                }
                """.formatted(REJECTED_VALUE);

        given()
                .contentType("application/json")
                .accept(PartyProblem.MEDIA_TYPE)
                .header("Tenant-Id", TENANT_ID)
                .header("User-Id", "service-account:party-registration")
                .header("Process-Id", PROCESS_ID)
                .body(request)
                .when()
                .post("/internal/v1/parties")
                .then()
                .statusCode(422)
                .contentType(PartyProblem.MEDIA_TYPE)
                .body("code", equalTo("INVALID_PARTY_DATA"))
                .body("violations[0].path", equalTo("$.naturalPersonDetails.unknown"))
                .body(not(containsString(REJECTED_VALUE)));

        assertEquals(0, useCase.callCount());
    }

    private static String requestWith(String field, String value) {
        return """
                {
                  "type": "NATURAL_PERSON",
                  "naturalPersonDetails": {
                    "givenNames": "Ana Maria",
                    "familyNames": "Example"
                  },
                  "%s": "%s"
                }
                """.formatted(field, value);
    }

    public static final class NoPersistenceProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.active", "false",
                    "quarkus.datasource.flyway.active", "false",
                    "quarkus.flyway.active", "false",
                    "quarkus.flyway.flyway.active", "false",
                    "quarkus.hibernate-orm.active", "false",
                    "quarkus.hibernate-orm.enabled", "false",
                    "quarkus.otel.sdk.disabled", "true");
        }
    }

    @Mock
    @ApplicationScoped
    public static class RecordingCreatePartyUseCase implements CreatePartyUseCase {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Uni<CreatePartyResult> create(CreatePartyCommand command, TrustedCreationContext context) {
            calls.incrementAndGet();
            return Uni.createFrom().failure(new UnsupportedOperationException("Creation is not part of this test"));
        }

        int callCount() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
