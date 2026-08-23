package com.alexastudillo.partyregistry.application.party.command;

import com.alexastudillo.partyregistry.domain.party.model.PartyType;
import java.util.List;

public record CreatePartyCommand(
        PartyType type,
        NaturalPersonInput naturalPersonDetails,
        LegalEntityInput legalEntityDetails,
        List<NationalityInput> nationalities) {
}
