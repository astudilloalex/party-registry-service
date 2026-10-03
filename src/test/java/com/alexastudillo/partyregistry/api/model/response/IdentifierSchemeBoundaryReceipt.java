package com.alexastudillo.partyregistry.api.model.response;

/** Exposes only test assertions about binding and execution, never an internal business result. */
public record IdentifierSchemeBoundaryReceipt(boolean eventLoop, String code, String minimumLength,
        boolean namePresent, boolean minimumPresent) {
}
