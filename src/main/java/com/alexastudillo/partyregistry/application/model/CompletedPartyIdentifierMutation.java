package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** Carries one tenant/operation-bound original safe completion and its private request digest and attribution. */
public record CompletedPartyIdentifierMutation(TenantId tenantId, PartyIdentifierOperation operation,
        String requestFingerprint, PartyIdentifierResult result, @Nullable PartyIdentifierVersion expectedVersion,
        String acceptedBy, Instant acceptedAt) {

    public CompletedPartyIdentifierMutation {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Identifier completion digest has an unsupported shape");
        }
        if (acceptedBy == null || acceptedBy.isBlank() || acceptedBy.codePointCount(0, acceptedBy.length()) > 128
                || acceptedBy.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Identifier completion actor is invalid");
        }
        if (operation == PartyIdentifierOperation.CREATE) {
            if (expectedVersion != null || result.version().value() != 0
                    || result.status() != PartyIdentifierStatus.PENDING_VERIFICATION) {
                throw new IllegalArgumentException("Identifier creation completion is inconsistent");
            }
        } else if (expectedVersion == null || expectedVersion.value() == Long.MAX_VALUE
                || result.version().value() != expectedVersion.value() + 1) {
            throw new IllegalArgumentException("Identifier action completion version is inconsistent");
        }
    }

    @Override
    public String toString() {
        return "CompletedPartyIdentifierMutation[operation=" + operation + ", fingerprint=<redacted>]";
    }
}
