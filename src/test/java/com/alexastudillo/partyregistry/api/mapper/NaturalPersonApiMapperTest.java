package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.response.NaturalPersonResponse;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonDetailResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies complete API projection without exposing application or domain
 * types.
 */
class NaturalPersonApiMapperTest {

    private static final UUID PARTY_ID = UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
    private static final UUID TENANT_ID = UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-30T13:30:00Z");

    private final NaturalPersonApiMapper mapper = new NaturalPersonApiMapper(new PartyIdentifierApiMapper());

    @Test
    void mapsEveryOpenApiResponseFieldAndFixesTheNaturalPersonType() {
        NaturalPersonResult result = new NaturalPersonResult(
                new PartyId(PARTY_ID),
                new TenantId(TENANT_ID),
                PartyType.LEGAL_ENTITY,
                "Ada Lovelace",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(4),
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.parse("1815-12-10"),
                LocalDate.parse("1852-11-27"),
                "GB",
                CREATED_AT,
                "creator",
                UPDATED_AT,
                "updater");

        NaturalPersonResponse response = mapper.toResponse(result);

        assertEquals(PARTY_ID, response.partyId());
        assertEquals("NATURAL_PERSON", response.type());
        assertEquals("Ada Lovelace", response.displayName());
        assertEquals("ACTIVE", response.recordStatus());
        assertEquals(4, response.version());
        assertEquals(CREATED_AT, response.createdAt());
        assertEquals(UPDATED_AT, response.updatedAt());
        assertEquals("creator", response.createdBy());
        assertEquals("updater", response.updatedBy());
        assertEquals("Ada", response.naturalPersonDetails().givenNames());
        assertEquals("Lovelace", response.naturalPersonDetails().familyNames());
        assertEquals("Ada", response.naturalPersonDetails().preferredName());
        assertEquals(LocalDate.parse("1815-12-10"), response.naturalPersonDetails().birthDate());
        assertEquals(LocalDate.parse("1852-11-27"), response.naturalPersonDetails().dateOfDeath());
        assertEquals("GB", response.naturalPersonDetails().birthCountryCode());

        PartyIdentifierResult identifier = new PartyIdentifierResult(
                new PartyIdentifierId(UUID.randomUUID()), new PartyId(PARTY_ID),
                new IdentifierSchemeId(UUID.randomUUID()), "EC_NATIONAL_ID", "******0009",
                PartyIdentifierStatus.VERIFIED, true, "AUTHORITY", null, null, UPDATED_AT, "verifier",
                new PartyIdentifierVersion(2), CREATED_AT, UPDATED_AT);
        var detail = mapper.toDetailResponse(new NaturalPersonDetailResult(result, List.of(identifier)));
        assertEquals(response.partyId(), detail.partyId());
        assertEquals(response.displayName(), detail.displayName());
        assertEquals(response.naturalPersonDetails(), detail.naturalPersonDetails());
        assertEquals(response.version(), detail.version());
        var detailIdentifiers = detail.identifiers();
        assertEquals(List.of(new PartyIdentifierApiMapper().toResponse(identifier)), detailIdentifiers);
        assertThrows(UnsupportedOperationException.class, detailIdentifiers::clear);
    }

    @Test
    void preservesNullOptionalDetailValues() {
        NaturalPersonResult result = new NaturalPersonResult(
                new PartyId(PARTY_ID),
                new TenantId(TENANT_ID),
                PartyType.NATURAL_PERSON,
                "Grace Hopper",
                PartyRecordStatus.DRAFT,
                PartyVersion.initial(),
                "Grace",
                "Hopper",
                null,
                null,
                null,
                null,
                CREATED_AT,
                "creator",
                CREATED_AT,
                "creator");

        NaturalPersonResponse response = mapper.toResponse(result);

        assertNull(response.naturalPersonDetails().preferredName());
        assertNull(response.naturalPersonDetails().birthDate());
        assertNull(response.naturalPersonDetails().dateOfDeath());
        assertNull(response.naturalPersonDetails().birthCountryCode());
        assertEquals(List.of(), mapper.toDetailResponse(new NaturalPersonDetailResult(result, List.of())).identifiers());
    }
}
