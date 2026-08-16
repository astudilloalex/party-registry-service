package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyType;

public record CreatePartyCommand(PartyType type, PartyDetailsView details) {}
