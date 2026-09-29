package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/** Encodes schema-five completed primary transfers, including the original no-op result. */
@ApplicationScoped
public class NationalityPrimarySnapshotCodec {

    private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String FINGERPRINT_FIELD = "fingerprint";
    private static final String REQUEST_FIELD = "request";
    private static final String RESULT_FIELD = "result";

    private static final String PARTY_ID_FIELD = "partyId";
    private static final String NATIONALITY_ID_FIELD = "nationalityId";

    private static final Set<String> ROOT_FIELDS = Set.of(
            SCHEMA_VERSION_FIELD, FINGERPRINT_FIELD, REQUEST_FIELD, RESULT_FIELD);
    private static final Set<String> INPUT_FIELDS = Set.of(PARTY_ID_FIELD, NATIONALITY_ID_FIELD);

    IdempotencyResultSnapshot encode(SetPrimaryNationalityCommand command, NationalityResult result) {
        validate(command, result);
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put(SCHEMA_VERSION_FIELD, NationalityOperation.SET_PRIMARY.schemaVersion());
        payload.put(FINGERPRINT_FIELD, NationalityRequestFingerprint.setPrimary(command));
        ObjectNode request = payload.putObject(REQUEST_FIELD);
        request.put(PARTY_ID_FIELD, command.partyId().value().toString());
        request.put(NATIONALITY_ID_FIELD, command.nationalityId().value().toString());
        payload.set(RESULT_FIELD, NationalitySnapshotFields.result(result));
        return new IdempotencyResultSnapshot(NationalityOperation.SET_PRIMARY.schemaVersion(), payload);
    }

    /** Restores the original 200 body even when current designations or the UTC date have since changed. */
    NationalityResult decode(IdempotencyResultSnapshot snapshot, SetPrimaryNationalityCommand command, String storedHash) {
        try {
            JsonNode node = snapshot.payload();
            NationalitySnapshotFields.fields(node, ROOT_FIELDS);
            if (snapshot.schemaVersion() != NationalityOperation.SET_PRIMARY.schemaVersion()
                    || node.get(SCHEMA_VERSION_FIELD) == null || !node.get(SCHEMA_VERSION_FIELD).isIntegralNumber()
                    || node.get(SCHEMA_VERSION_FIELD).intValue() != NationalityOperation.SET_PRIMARY.schemaVersion()
                    || !NationalityRequestFingerprint.matches(storedHash, NationalitySnapshotFields.text(node, FINGERPRINT_FIELD))) {
                throw NationalitySnapshotFields.invalid();
            }
            JsonNode request = node.get(REQUEST_FIELD);
            NationalitySnapshotFields.fields(request, INPUT_FIELDS);
            if (!NationalitySnapshotFields.uuid(request, PARTY_ID_FIELD).equals(command.partyId().value())
                    || !NationalitySnapshotFields.uuid(request, NATIONALITY_ID_FIELD).equals(command.nationalityId().value())) {
                throw NationalitySnapshotFields.invalid();
            }
            NationalityResult result = NationalitySnapshotFields.readResult(node.get(RESULT_FIELD));
            validate(command, result);
            return result;
        } catch (RuntimeException _) {
            throw NationalitySnapshotFields.invalid();
        }
    }

    private static void validate(SetPrimaryNationalityCommand command, NationalityResult result) {
        if (!result.partyId().equals(command.partyId()) || !result.nationalityId().equals(command.nationalityId())
                || !result.isPrimary()) {
            throw NationalitySnapshotFields.invalid();
        }
    }
}
