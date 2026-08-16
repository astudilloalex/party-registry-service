package com.alexastudillo.partyregistry.contractvalidation;

final class ContractValidationException extends RuntimeException {
    ContractValidationException(String message) {
        super(message);
    }

    ContractValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
