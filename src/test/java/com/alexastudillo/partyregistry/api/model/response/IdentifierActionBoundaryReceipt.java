package com.alexastudillo.partyregistry.api.model.response;

/** Reports test-only execution/presence/count evidence without exposing submitted reason content. */
public record IdentifierActionBoundaryReceipt(boolean eventLoop, boolean codePresent, int codePoints, boolean detailPresent, int detailPoints) { }
