package com.alexastudillo.partyregistry.api.rest.v1.party.mapper;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.alexastudillo.partyregistry.api.rest.v1.party.dto.CreatePartyRequest;
import com.alexastudillo.partyregistry.application.party.command.CreatePartyCommand;
import com.alexastudillo.partyregistry.application.party.command.LegalEntityInput;
import com.alexastudillo.partyregistry.application.party.command.NationalityInput;
import com.alexastudillo.partyregistry.application.party.command.NaturalPersonInput;
import com.alexastudillo.partyregistry.domain.party.model.PartyType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PartyRequestMapper {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "type", "naturalPersonDetails", "legalEntityDetails", "nationalities");
    private static final Set<String> NATURAL_PERSON_FIELDS = Set.of(
            "givenNames", "familyNames", "preferredName", "birthDate", "dateOfDeath", "birthCountryCode");
    private static final Set<String> LEGAL_ENTITY_FIELDS = Set.of(
            "legalName", "tradeName", "legalFormCode", "incorporationCountryCode", "incorporatedOn", "dissolvedOn");
    private static final Set<String> NATIONALITY_FIELDS = Set.of(
            "countryCode", "isPrimary", "validFrom", "validUntil");
    private static final Pattern SAFE_PROPERTY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");

    private final ObjectMapper objectMapper;

    public PartyRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CreatePartyCommand map(JsonNode request) {
        requireClosedObject(request, ROOT_FIELDS, "$");
        validateNestedObject(request, "naturalPersonDetails", NATURAL_PERSON_FIELDS);
        validateNestedObject(request, "legalEntityDetails", LEGAL_ENTITY_FIELDS);
        validateNationalities(request.get("nationalities"));

        try {
            return map(objectMapper.treeToValue(request, CreatePartyRequest.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidPartyRequestException("$");
        }
    }

    public CreatePartyCommand map(CreatePartyRequest request) {
        NaturalPersonInput naturalPerson = request.naturalPersonDetails() == null
                ? null
                : new NaturalPersonInput(
                        request.naturalPersonDetails().givenNames(),
                        request.naturalPersonDetails().familyNames(),
                        request.naturalPersonDetails().preferredName(),
                        request.naturalPersonDetails().birthDate(),
                        request.naturalPersonDetails().dateOfDeath(),
                        request.naturalPersonDetails().birthCountryCode());
        LegalEntityInput legalEntity = request.legalEntityDetails() == null
                ? null
                : new LegalEntityInput(
                        request.legalEntityDetails().legalName(),
                        request.legalEntityDetails().tradeName(),
                        request.legalEntityDetails().legalFormCode(),
                        request.legalEntityDetails().incorporationCountryCode(),
                        request.legalEntityDetails().incorporatedOn(),
                        request.legalEntityDetails().dissolvedOn());
        List<NationalityInput> nationalities = request.nationalities() == null
                ? List.of()
                : request.nationalities().stream()
                        .map(value -> new NationalityInput(
                                value.countryCode(),
                                value.isPrimary(),
                                value.validFrom(),
                                value.validUntil()))
                        .toList();
        return new CreatePartyCommand(PartyType.valueOf(request.type()), naturalPerson, legalEntity, nationalities);
    }

    private static void validateNestedObject(JsonNode root, String fieldName, Set<String> allowedFields) {
        JsonNode nested = root.get(fieldName);
        if (nested != null && !nested.isNull()) {
            requireClosedObject(nested, allowedFields, "$." + fieldName);
        }
    }

    private static void validateNationalities(JsonNode nationalities) {
        if (nationalities == null || nationalities.isNull()) {
            return;
        }
        if (!nationalities.isArray()) {
            throw new InvalidPartyRequestException("$.nationalities");
        }
        for (int index = 0; index < nationalities.size(); index++) {
            requireClosedObject(nationalities.get(index), NATIONALITY_FIELDS, "$.nationalities[" + index + "]");
        }
    }

    private static void requireClosedObject(JsonNode value, Set<String> allowedFields, String path) {
        if (value == null || !value.isObject()) {
            throw new InvalidPartyRequestException(path);
        }
        value.propertyStream()
                .map(java.util.Map.Entry::getKey)
                .filter(fieldName -> !allowedFields.contains(fieldName))
                .findFirst()
                .ifPresent(fieldName -> {
                    throw new InvalidPartyRequestException(sanitizedPath(path, fieldName));
                });
    }

    private static String sanitizedPath(String parentPath, String fieldName) {
        return SAFE_PROPERTY_NAME.matcher(fieldName).matches()
                ? parentPath + "." + fieldName
                : parentPath + ".*";
    }

    public static final class InvalidPartyRequestException extends RuntimeException {

        private final String path;

        public InvalidPartyRequestException(String path) {
            super("The request contains invalid Party data");
            this.path = path;
        }

        public String path() {
            return path;
        }
    }
}
