package com.alexastudillo.partyregistry.application.model;

/**
 * Enumerates safe terminal outcomes for observed application and adapter operations.
 */
public enum OperationOutcome {
    CREATED,
    REPLAYED,
    ACTIVATED,
    VALIDATION_FAILED,
    CONFLICT,
    IDENTIFIER_CONFLICT,
    NOT_FOUND,
    PRECONDITION_FAILED,
    DEPENDENCY_UNAVAILABLE,
    CANCELLED,
    INTERNAL_FAILURE
}
