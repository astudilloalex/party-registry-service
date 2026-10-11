package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.api.error.GlobalErrorContractTestProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies actual shared-envelope serialization, safe DTO closure, explicit null navigation and checked count capacity. */
@QuarkusTest
@TestProfile(GlobalErrorContractTestProfile.class)
class IdentifierSearchApiMapperTest {
    @Inject IdentifierSearchApiMapper mapper;
    @Inject ResponseManager responses;
    @Inject ObjectMapper json;

    @Test
    void serializesOnlySafeStoredFieldsAndCompleteExplicitMetadata() throws Exception {
        var hit = new IdentifierSearchHit(new PartyId(new UUID(1, 1)), PartyType.LEGAL_ENTITY, "Stored Label", PartyRecordStatus.ARCHIVED,
                "SEARCH", "***001", PartyIdentifierStatus.REVOKED, false,
                new PartyIdentifierPagePosition(Instant.EPOCH, new PartyIdentifierId(new UUID(2, 2))));
        var page = IdentifierSearchPage.exact(new ExactIdentifierSearchSlice(List.of(hit), 1));
        var response = responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page));
        var assertions = new IdentifierSearchContractAssertions(IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml")));
        assertions.assertExactResponse(response.getStatus(), json.writeValueAsString(response.getEntity()));
        assertEquals("Stored Label", mapper.toResponses(page).getFirst().displayName());
        assertEquals("***001", mapper.toResponses(page).getFirst().identifier().maskedValue());
        var empty = IdentifierSearchPage.prefix(new PrefixIdentifierSearchSlice(List.of(), 0, Optional.empty(), Optional.empty()), 50, null, null);
        var emptyResponse = responses.paginatedHttp(mapper.toResponses(empty), mapper.toPagination(empty));
        assertions.assertPrefixResponse(emptyResponse.getStatus(), json.writeValueAsString(emptyResponse.getEntity()), 50);
    }

    @Test
    void rejectsUnsupportedCountCapacityWithoutWrappedOrMissingCounts() {
        var tooLarge = new IdentifierSearchPage(List.of(), (long) Integer.MAX_VALUE + 1, 1, null, null);
        assertThrows(ArithmeticException.class, () -> mapper.toPagination(tooLarge));
    }
}
