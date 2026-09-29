package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.Set;

/**
 * Encodes schema-four completed creation inputs and immutable detached results.
 */
@ApplicationScoped
public class NationalityCreateSnapshotCodec {

    private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String FINGERPRINT_FIELD = "fingerprint";
    private static final String REQUEST_FIELD = "request";
    private static final String RESULT_FIELD = "result";

    private static final String PARTY_ID_FIELD = "partyId";
    private static final String COUNTRY_CODE_FIELD = "countryCode";
    private static final String IS_PRIMARY_FIELD = "isPrimary";
    private static final String VALID_FROM_FIELD = "validFrom";
    private static final String VALID_UNTIL_FIELD = "validUntil";

    private static final Set<String> ROOT_FIELDS = Set.of(
            SCHEMA_VERSION_FIELD, FINGERPRINT_FIELD, REQUEST_FIELD, RESULT_FIELD);
    private static final Set<String> INPUT_FIELDS = Set.of(
            PARTY_ID_FIELD, COUNTRY_CODE_FIELD, IS_PRIMARY_FIELD, VALID_FROM_FIELD, VALID_UNTIL_FIELD);

    IdempotencyResultSnapshot encode(CreateNationalityCommand command, NationalityResult result) {
        validate(command, result);
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put(SCHEMA_VERSION_FIELD, NationalityOperation.CREATE.schemaVersion());
        payload.put(FINGERPRINT_FIELD, NationalityRequestFingerprint.create(command));
        ObjectNode request = payload.putObject(REQUEST_FIELD);
        request.put(PARTY_ID_FIELD, command.partyId().value().toString());
        request.put(COUNTRY_CODE_FIELD, command.countryCode());
        request.put(IS_PRIMARY_FIELD, command.isPrimary());
        NationalitySnapshotFields.date(request, VALID_FROM_FIELD, command.period().validFrom());
        NationalitySnapshotFields.date(request, VALID_UNTIL_FIELD, command.period().validUntil());
        payload.set(RESULT_FIELD, NationalitySnapshotFields.result(result));
        return new IdempotencyResultSnapshot(NationalityOperation.CREATE.schemaVersion(), payload);
    }

    /**
     * Restores an original 201 body after checking the stored operation, input hash
     * and Party identity.
     */
    NationalityResult decode(IdempotencyResultSnapshot snapshot, CreateNationalityCommand command, String storedHash) {
        try {
            JsonNode node = snapshot.payload();
            NationalitySnapshotFields.fields(node, ROOT_FIELDS);
            if (snapshot.schemaVersion() != NationalityOperation.CREATE.schemaVersion()
                    || node.get(SCHEMA_VERSION_FIELD) == null || !node.get(SCHEMA_VERSION_FIELD).isIntegralNumber()
                    || node.get(SCHEMA_VERSION_FIELD).intValue() != NationalityOperation.CREATE.schemaVersion()
                    || !NationalityRequestFingerprint.matches(storedHash,
                            NationalitySnapshotFields.text(node, FINGERPRINT_FIELD))) {
                throw NationalitySnapshotFields.invalid();
            }
            JsonNode request = node.get(REQUEST_FIELD);
            NationalitySnapshotFields.fields(request, INPUT_FIELDS);
            if (!NationalitySnapshotFields.uuid(request, PARTY_ID_FIELD).equals(command.partyId().value())
                    || !NationalitySnapshotFields.text(request, COUNTRY_CODE_FIELD).equals(command.countryCode())
                    || NationalitySnapshotFields.booleanValue(request, IS_PRIMARY_FIELD) != command.isPrimary()
                    || !Objects.equals(NationalitySnapshotFields.date(request, VALID_FROM_FIELD),
                            command.period().validFrom())
                    || !Objects.equals(NationalitySnapshotFields.date(request, VALID_UNTIL_FIELD),
                            command.period().validUntil())) {
                throw NationalitySnapshotFields.invalid();
            }
            NationalityResult result = NationalitySnapshotFields.readResult(node.get(RESULT_FIELD));
            validate(command, result);
            return result;
        } catch (RuntimeException _) {
            throw NationalitySnapshotFields.invalid();
        }
    }

    private static void validate(CreateNationalityCommand command, NationalityResult result) {
        if (!result.partyId().equals(command.partyId()) || !result.countryCode().equals(command.countryCode())
                || result.isPrimary() != command.isPrimary()
                || !Objects.equals(result.validFrom(), command.period().validFrom())
                || !Objects.equals(result.validUntil(), command.period().validUntil())) {
            throw NationalitySnapshotFields.invalid();
        }
    }
}
