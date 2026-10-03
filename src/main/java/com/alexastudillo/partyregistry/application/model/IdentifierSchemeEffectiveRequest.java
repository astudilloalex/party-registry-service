package com.alexastudillo.partyregistry.application.model;

/** Identifies exact catalog intent independently of request attribution and correlation. */
public sealed interface IdentifierSchemeEffectiveRequest
        permits IdentifierSchemeCreateInput, IdentifierSchemeLifecycleRequest {

    /** Returns the durable operation namespace used for completion isolation. */
    String operation();
}
