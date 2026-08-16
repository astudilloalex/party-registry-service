package com.alexastudillo.partyregistry.application;

public record NationalitySearchQuery(
        String countryCode, Boolean primary, Boolean active, PageRequest page) {
    public NationalitySearchQuery {
        if (page == null) {
            page = PageRequest.defaults();
        }
    }
}
