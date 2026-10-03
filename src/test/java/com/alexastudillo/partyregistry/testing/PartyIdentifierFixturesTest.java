package com.alexastudillo.partyregistry.testing;

import com.alexastudillo.partyregistry.api.mapper.PartyIdentifierApiMapper;
import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes controlled owner, timestamp, history, and safe-projection fixtures for identifier increments. */
class PartyIdentifierFixturesTest {

    @Test
    void clockAndCreationBoundariesRemainDeterministicAtUtcAndPostgresPrecision() {
        assertEquals(ZoneOffset.UTC, PartyIdentifierFixtures.CLOCK.getZone());
        assertEquals(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.CLOCK.instant());
        assertEquals(PartyIdentifierFixtures.EVALUATED_ON, LocalDate.now(PartyIdentifierFixtures.CLOCK));
        assertTrue(PartyIdentifierFixtures.CREATED_AT.isBefore(PartyIdentifierFixtures.OCCURRED_AT));
        assertEquals(0, PartyIdentifierFixtures.OCCURRED_AT.getNano() % 1000);
    }

    @Test
    void bothIndependentOwnerTypesSupportMaskedHistoricalFixtures() {
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var mapper = new PartyIdentifierApiMapper();
        assertNotEquals(PartyIdentifierFixtures.owner(PartyType.NATURAL_PERSON).partyId(),
                PartyIdentifierFixtures.owner(PartyType.LEGAL_ENTITY).partyId());
        for (PartyType type : PartyType.values()) {
            var owner = PartyIdentifierFixtures.owner(type);
            var identifier = PartyIdentifierFixtures.identifier(type, PartyIdentifierStatus.REVOKED,
                    false, 3, PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
            assertEquals(type, owner.type());
            assertEquals(owner.partyId(), identifier.partyId());
            assertEquals(owner.tenantId(), identifier.tenantId());
            assertEquals(PartyIdentifierFixtures.CREATOR, identifier.verifiedBy());
            assertEquals(PartyIdentifierFixtures.CREATED_AT, identifier.verifiedAt());
            var result = PartyIdentifierFixtures.result(identifier);
            assertEquals("******1234", result.maskedValue());
            PartyIdentifierContractAssertions.assertItem(json.valueToTree(mapper.toResponse(result)));
        }
    }
}
