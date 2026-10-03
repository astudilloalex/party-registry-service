package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeEffectiveRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeLengthBounds;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stores explicit version-one scheme intent and historical acceptance, validating corruption without rule readmission. */
@ApplicationScoped
public class IdentifierSchemeCompletionCodec {

    static final short SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String TENANT_ID_FIELD = "tenantId";
    private static final String OPERATION_FIELD = "operation";
    private static final String REQUEST_FIELD = "request";
    private static final String SCHEME_FIELD = "scheme";

    private static final String CODE_FIELD = "code";
    private static final String ISSUING_COUNTRY_CODE_FIELD = "issuingCountryCode";
    private static final String CATEGORY_FIELD = "category";
    private static final String APPLICABLE_SUBJECT_TYPE_FIELD = "applicableSubjectType";
    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String NORMALIZER_KEY_FIELD = "normalizerKey";
    private static final String VALIDATOR_KEY_FIELD = "validatorKey";
    private static final String MINIMUM_LENGTH_FIELD = "minimumLength";
    private static final String MAXIMUM_LENGTH_FIELD = "maximumLength";
    private static final String REQUIRES_EXPIRATION_FIELD = "requiresExpiration";

    private static final String ACTION_FIELD = "action";
    private static final String SCHEME_ID_FIELD = "schemeId";
    private static final String EXPECTED_VERSION_FIELD = "expectedVersion";

    private static final String ID_FIELD = "id";
    private static final String STATUS_FIELD = "status";
    private static final String VERSION_FIELD = "version";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String CREATED_BY_FIELD = "createdBy";
    private static final String UPDATED_AT_FIELD = "updatedAt";
    private static final String UPDATED_BY_FIELD = "updatedBy";

    private static final Set<String> ROOT_FIELDS = Set.of(
            SCHEMA_VERSION_FIELD, TENANT_ID_FIELD, OPERATION_FIELD, REQUEST_FIELD, SCHEME_FIELD);
    private static final Set<String> CREATE_FIELDS = Set.of(
            CODE_FIELD, ISSUING_COUNTRY_CODE_FIELD, CATEGORY_FIELD, APPLICABLE_SUBJECT_TYPE_FIELD,
            NAME_FIELD, DESCRIPTION_FIELD, NORMALIZER_KEY_FIELD, VALIDATOR_KEY_FIELD,
            MINIMUM_LENGTH_FIELD, MAXIMUM_LENGTH_FIELD, REQUIRES_EXPIRATION_FIELD);
    private static final Set<String> LIFECYCLE_FIELDS = Set.of(
            ACTION_FIELD, SCHEME_ID_FIELD, EXPECTED_VERSION_FIELD);
    private static final Set<String> SCHEME_FIELDS = Set.of(
            ID_FIELD, CODE_FIELD, ISSUING_COUNTRY_CODE_FIELD, CATEGORY_FIELD, APPLICABLE_SUBJECT_TYPE_FIELD,
            NAME_FIELD, DESCRIPTION_FIELD, NORMALIZER_KEY_FIELD, VALIDATOR_KEY_FIELD,
            MINIMUM_LENGTH_FIELD, MAXIMUM_LENGTH_FIELD, REQUIRES_EXPIRATION_FIELD,
            STATUS_FIELD, VERSION_FIELD, CREATED_AT_FIELD, CREATED_BY_FIELD, UPDATED_AT_FIELD, UPDATED_BY_FIELD);

    /** Produces a fresh detached tree after checking the full accepted result and its effective intent. */
    IdempotencyResultSnapshot encode(TenantId tenant, CompletedIdentifierSchemeOperation completed) {
        try {
            Objects.requireNonNull(tenant, "tenant");
            validate(completed);
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            root.put(SCHEMA_VERSION_FIELD, SCHEMA_VERSION);
            root.put(TENANT_ID_FIELD, tenant.value().toString());
            root.put(OPERATION_FIELD, completed.request().operation());
            ObjectNode request = root.putObject(REQUEST_FIELD);
            switch (completed.request()) {
                case IdentifierSchemeCreateInput create -> encodeConfiguration(request, create);
                case IdentifierSchemeLifecycleRequest(var action, var schemeId, var expectedVersion) -> {
                    request.put(ACTION_FIELD, action.name());
                    request.put(SCHEME_ID_FIELD, schemeId.value().toString());
                    request.put(EXPECTED_VERSION_FIELD, expectedVersion.value());
                }
            }
            var result = completed.result();
            ObjectNode scheme = root.putObject(SCHEME_FIELD);
            encodeConfiguration(scheme, configuration(result));
            scheme.put(ID_FIELD, result.id().value().toString());
            scheme.put(STATUS_FIELD, result.status().name());
            scheme.put(VERSION_FIELD, result.version().value());
            scheme.put(CREATED_AT_FIELD, result.createdAt().toString());
            scheme.put(CREATED_BY_FIELD, result.createdBy());
            scheme.put(UPDATED_AT_FIELD, result.updatedAt().toString());
            scheme.put(UPDATED_BY_FIELD, result.updatedBy());
            return new IdempotencyResultSnapshot(SCHEMA_VERSION, root);
        } catch (RuntimeException _) {
            throw invalidSnapshot();
        }
    }

    /** Validates all persisted metadata, retaining saved intent for subsequent full retry equivalence checks. */
    CompletedIdentifierSchemeOperation decode(IdentifierSchemeIdempotencyRecordEntity stored) {
        try {
            return decode(new IdempotencyResultSnapshot(stored.resultSnapshotSchemaVersion(), stored.resultSnapshot()),
                    new TenantId(stored.id().tenantId()), stored.id().operation(),
                    new IdentifierSchemeId(stored.identifierSchemeId()), stored.requestHash(), stored.createdAt(), stored.createdBy());
        } catch (RuntimeException _) {
            throw invalidSnapshot();
        }
    }

    /** Checks saved scope and attribution, never a new retry's actor, target, or expected version. */
    CompletedIdentifierSchemeOperation decode(IdempotencyResultSnapshot snapshot, TenantId storedTenant,
            String storedOperation, IdentifierSchemeId storedTarget, String storedHash,
            Instant acceptedAt, String acceptedBy) {
        try {
            JsonNode root = snapshot.payload().deepCopy();
            requireFields(root, ROOT_FIELDS);
            require(snapshot.schemaVersion() == SCHEMA_VERSION && requiredLong(root, SCHEMA_VERSION_FIELD) == SCHEMA_VERSION);
            require(requiredUuid(root, TENANT_ID_FIELD).equals(storedTenant.value()));
            String operation = requiredText(root, OPERATION_FIELD);
            require(operation.equals(storedOperation));
            var request = decodeRequest(root.get(REQUEST_FIELD), operation);
            var result = decodeResult(root.get(SCHEME_FIELD));
            var completed = new CompletedIdentifierSchemeOperation(request, result);
            validate(completed);
            require(result.id().equals(storedTarget));
            require(IdentifierSchemeRequestFingerprint.matches(storedHash,
                    IdentifierSchemeRequestFingerprint.fingerprint(storedTenant, request)));
            require(result.updatedAt().equals(acceptedAt) && result.updatedBy().equals(acceptedBy));
            return completed;
        } catch (RuntimeException _) {
            throw invalidSnapshot();
        }
    }

    private static IdentifierSchemeEffectiveRequest decodeRequest(JsonNode node, String operation) {
        if (operation.equals("identifier-scheme.create.v1")) {
            requireFields(node, CREATE_FIELDS);
            return decodeConfiguration(node);
        }
        requireFields(node, LIFECYCLE_FIELDS);
        var action = IdentifierSchemeLifecycleAction.valueOf(requiredText(node, ACTION_FIELD));
        require(action.operation().equals(operation));
        return new IdentifierSchemeLifecycleRequest(action, new IdentifierSchemeId(requiredUuid(node, SCHEME_ID_FIELD)),
                new IdentifierSchemeVersion(requiredLong(node, EXPECTED_VERSION_FIELD)));
    }

    private static IdentifierSchemeResult decodeResult(JsonNode node) {
        requireFields(node, SCHEME_FIELDS);
        var config = decodeConfiguration(node);
        var bounds = IdentifierSchemeLengthBounds.fromIntegralInputs(config.minimumLength(), config.maximumLength());
        return new IdentifierSchemeResult(new IdentifierSchemeId(requiredUuid(node, ID_FIELD)), config.code(),
                config.issuingCountryCode(), config.category(), config.applicableSubjectType(), config.name(),
                config.description(), config.normalizerKey(), config.validatorKey(), bounds.minimumLength(), bounds.maximumLength(),
                config.requiresExpiration(), IdentifierSchemeStatus.valueOf(requiredText(node, STATUS_FIELD)),
                new IdentifierSchemeVersion(requiredLong(node, VERSION_FIELD)), Instant.parse(requiredText(node, CREATED_AT_FIELD)),
                requiredText(node, CREATED_BY_FIELD), Instant.parse(requiredText(node, UPDATED_AT_FIELD)), requiredText(node, UPDATED_BY_FIELD));
    }

    private static IdentifierSchemeCreateInput decodeConfiguration(JsonNode node) {
        return new IdentifierSchemeCreateInput(requiredText(node, CODE_FIELD), requiredText(node, ISSUING_COUNTRY_CODE_FIELD),
                IdentifierCategory.valueOf(requiredText(node, CATEGORY_FIELD)),
                IdentifierSubjectType.valueOf(requiredText(node, APPLICABLE_SUBJECT_TYPE_FIELD)), requiredText(node, NAME_FIELD),
                nullableText(node, DESCRIPTION_FIELD), requiredText(node, NORMALIZER_KEY_FIELD), requiredText(node, VALIDATOR_KEY_FIELD),
                nullableInteger(node, MINIMUM_LENGTH_FIELD), nullableInteger(node, MAXIMUM_LENGTH_FIELD), requiredBoolean(node, REQUIRES_EXPIRATION_FIELD));
    }

    private static void encodeConfiguration(ObjectNode node, IdentifierSchemeCreateInput config) {
        node.put(CODE_FIELD, config.code());
        node.put(ISSUING_COUNTRY_CODE_FIELD, config.issuingCountryCode());
        node.put(CATEGORY_FIELD, config.category().name());
        node.put(APPLICABLE_SUBJECT_TYPE_FIELD, config.applicableSubjectType().name());
        node.put(NAME_FIELD, config.name());
        node.put(DESCRIPTION_FIELD, config.description());
        node.put(NORMALIZER_KEY_FIELD, config.normalizerKey());
        node.put(VALIDATOR_KEY_FIELD, config.validatorKey());
        node.put(MINIMUM_LENGTH_FIELD, config.minimumLength());
        node.put(MAXIMUM_LENGTH_FIELD, config.maximumLength());
        node.put(REQUIRES_EXPIRATION_FIELD, config.requiresExpiration());
    }

    private static IdentifierSchemeCreateInput configuration(IdentifierSchemeResult result) {
        return new IdentifierSchemeCreateInput(result.code(), result.issuingCountryCode(), result.category(), result.applicableSubjectType(),
                result.name(), result.description(), result.normalizerKey(), result.validatorKey(), integral(result.minimumLength()),
                integral(result.maximumLength()), result.requiresExpiration());
    }

    private static void validate(CompletedIdentifierSchemeOperation completed) {
        var result = completed.result();
        // Restore all Domain invariants, but historical keys are not subject to current admission policy.
        result.toAggregate();
        new IdentifierSchemeLengthBounds(result.minimumLength(), result.maximumLength());
        switch (completed.request()) {
            case IdentifierSchemeCreateInput create -> {
                require(create.equals(configuration(result)));
                require(result.status() == IdentifierSchemeStatus.DRAFT && result.version().value() == 0);
                require(result.createdAt().equals(result.updatedAt()) && result.createdBy().equals(result.updatedBy()));
            }
            case IdentifierSchemeLifecycleRequest(var action, var schemeId, var expectedVersion) -> {
                require(schemeId.equals(result.id()));
                require(expectedVersion.value() != Long.MAX_VALUE);
                require(result.version().value() == expectedVersion.value() + 1);
                var expectedStatus = switch (action) {
                    case ACTIVATE -> IdentifierSchemeStatus.ACTIVE;
                    case DEPRECATE -> IdentifierSchemeStatus.DEPRECATED;
                    case RETIRE -> IdentifierSchemeStatus.RETIRED;
                };
                require(result.status() == expectedStatus && result.updatedAt().isAfter(result.createdAt()));
            }
        }
    }

    private static void requireFields(@Nullable JsonNode node, Set<String> fields) {
        require(node != null && node.isObject() && node.size() == fields.size()
                && node.properties().stream().allMatch(entry -> fields.contains(entry.getKey())));
    }

    private static JsonNode requiredValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw invalidSnapshot();
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        var value = requiredValue(node, field);
        require(value.isTextual());
        return value.textValue();
    }

    private static @Nullable String nullableText(JsonNode node, String field) {
        var value = requiredValue(node, field);
        return value.isNull() ? null : requiredText(node, field);
    }

    private static @Nullable BigInteger nullableInteger(JsonNode node, String field) {
        var value = requiredValue(node, field);
        if (value.isNull()) {
            return null;
        }
        require(value.isIntegralNumber());
        return value.bigIntegerValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        var value = requiredValue(node, field);
        require(value.isBoolean());
        return value.booleanValue();
    }

    private static long requiredLong(JsonNode node, String field) {
        var value = requiredValue(node, field);
        require(value.isIntegralNumber() && value.canConvertToLong());
        return value.longValue();
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String text = requiredText(node, field);
        var uuid = UUID.fromString(text);
        require(uuid.toString().equals(text));
        return uuid;
    }

    private static @Nullable BigInteger integral(@Nullable Integer value) {
        return value == null ? null : BigInteger.valueOf(value);
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw invalidSnapshot();
        }
    }

    private static ApplicationException invalidSnapshot() {
        return new ApplicationException(new ApplicationFailure.PersistenceFailure(),
                new IllegalStateException("Stored identifier scheme completion is invalid"));
    }
}
