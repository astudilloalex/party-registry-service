package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyStatus;
import com.alexastudillo.partyregistry.domain.PartyType;

public record PartySearchQuery(PartyType type, PartyStatus status, PageRequest page) {
    public PartySearchQuery {
        if (page == null) {
            page = PageRequest.defaults();
        }
    }
}
