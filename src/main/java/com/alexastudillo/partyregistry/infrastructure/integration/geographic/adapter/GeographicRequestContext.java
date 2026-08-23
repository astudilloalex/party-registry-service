package com.alexastudillo.partyregistry.infrastructure.integration.geographic.adapter;

public record GeographicRequestContext(String tenantHeaderValue, String userId, String processHeaderValue) {
}
