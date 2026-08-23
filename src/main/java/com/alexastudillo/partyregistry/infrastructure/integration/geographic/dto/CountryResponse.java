package com.alexastudillo.partyregistry.infrastructure.integration.geographic.dto;

public record CountryResponse(Integer status, String code, CountryData data) {

    public record CountryData(String status) {
    }
}
