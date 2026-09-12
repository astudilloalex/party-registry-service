package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.model.response.InitialPartyIdentifierResponse;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonCreateResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyIdentifierResponse;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies complete, exhaustive, and confidential API result projection.
 */
class PartyApiMapperTest {

    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-30T13:30:00Z");

    private final PartyIdentifierApiMapper identifierMapper = new PartyIdentifierApiMapper();
    private final NaturalPersonApiMapper naturalMapper = new NaturalPersonApiMapper(identifierMapper);
    private final LegalEntityApiMapper legalMapper = new LegalEntityApiMapper(identifierMapper);
    private final PartyApiMapper partyMapper = new PartyApiMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void mapsAllSafeIdentifierFieldsWithoutProtectedMaterial() throws Exception {
        PartyIdentifierResponse response = identifierMapper.toResponse(identifier());

        assertEquals("TEST_NATURAL_ACTIVE", response.schemeCode());
        assertEquals("******56", response.maskedValue());
        assertEquals("PENDING_VERIFICATION", response.status());
        assertEquals("AUTHORITY", response.issuerCode());
        assertEquals(LocalDate.parse("2026-01-01"), response.issuedOn());
        assertEquals(LocalDate.parse("2030-01-01"), response.expiresOn());
        assertEquals(0, response.version());
        assertEquals(CREATED_AT, response.createdAt());
        assertEquals(UPDATED_AT, response.updatedAt());

        Set<String> components = java.util.Arrays.stream(PartyIdentifierResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(components.contains("value"));
        assertFalse(components.contains("encryptedValue"));
        assertFalse(components.contains("normalizedValue"));
        assertFalse(components.contains("normalizedValueHash"));
        assertFalse(components.contains("encryptionKeyVersion"));
        assertFalse(objectMapper.writeValueAsString(response).contains("AB123456"));
    }

    @Test
    void mapsNaturalAndLegalCreateOnlyResponsesWithPendingInitialIdentifier() {
        NaturalPersonCreateResponse natural = naturalMapper.toCreateResponse(new PartyRegistrationResult(
                naturalPerson(), identifier(), PartyRegistrationOutcome.CREATED));
        LegalEntityCreateResponse legal = legalMapper.toCreateResponse(new PartyRegistrationResult(
                legalEntity(), identifier(), PartyRegistrationOutcome.REPLAYED));

        assertEquals("NATURAL_PERSON", natural.type());
        assertEquals("Ada", natural.naturalPersonDetails().givenNames());
        assertInitialIdentifier(natural.initialIdentifier());
        assertEquals("LEGAL_ENTITY", legal.type());
        assertEquals("Analytical Engines Ltd", legal.legalEntityDetails().legalName());
        assertInitialIdentifier(legal.initialIdentifier());
    }

    @Test
    void exhaustivelyProjectsOnlyTheMatchingSubtypeDetails() throws Exception {
        PartyDetailResponse natural = partyMapper.toResponse(naturalPerson());
        PartyDetailResponse legal = partyMapper.toResponse(legalEntity());

        assertEquals("NATURAL_PERSON", natural.type());
        assertTrue(natural.naturalPersonDetails() != null);
        assertNull(natural.legalEntityDetails());
        assertFalse(objectMapper.writeValueAsString(natural).contains("legalEntityDetails"));
        assertEquals("LEGAL_ENTITY", legal.type());
        assertNull(legal.naturalPersonDetails());
        assertTrue(legal.legalEntityDetails() != null);
        assertFalse(objectMapper.writeValueAsString(legal).contains("naturalPersonDetails"));
    }

    private static void assertInitialIdentifier(InitialPartyIdentifierResponse identifier) {
        assertEquals(PARTY_ID.value(), identifier.partyId());
        assertEquals("PENDING_VERIFICATION", identifier.status());
        assertEquals("******56", identifier.maskedValue());
    }

    private static PartyIdentifierResult identifier() {
        return new PartyIdentifierResult(
                new PartyIdentifierId(UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744")),
                PARTY_ID,
                new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd601")),
                "TEST_NATURAL_ACTIVE",
                "******56",
                PartyIdentifierStatus.PENDING_VERIFICATION,
                true,
                "AUTHORITY",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2030-01-01"),
                null,
                null,
                PartyIdentifierVersion.initial(),
                CREATED_AT,
                UPDATED_AT);
    }

    private static NaturalPersonResult naturalPerson() {
        return new NaturalPersonResult(
                PARTY_ID,
                TENANT_ID,
                PartyType.NATURAL_PERSON,
                "Ada Lovelace",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(2),
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
    }

    private static LegalEntityResult legalEntity() {
        return new LegalEntityResult(
                PARTY_ID,
                TENANT_ID,
                PartyType.LEGAL_ENTITY,
                "Analytical Engines Ltd",
                PartyRecordStatus.DRAFT,
                PartyVersion.initial(),
                "Analytical Engines Ltd",
                "Analytical",
                "LTD",
                "EC",
                LocalDate.parse("2020-01-01"),
                null,
                CREATED_AT,
                "creator",
                UPDATED_AT,
                "updater");
    }
}
