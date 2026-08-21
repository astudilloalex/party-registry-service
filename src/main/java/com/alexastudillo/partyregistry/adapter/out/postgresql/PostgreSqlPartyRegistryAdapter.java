package com.alexastudillo.partyregistry.adapter.out.postgresql;

import com.alexastudillo.partyregistry.application.ApplicationFailure;
import com.alexastudillo.partyregistry.application.AuditFacts;
import com.alexastudillo.partyregistry.application.IdentifierMutation;
import com.alexastudillo.partyregistry.application.IdentifierMutationIntent;
import com.alexastudillo.partyregistry.application.IdentifierSchemeMetadata;
import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityMutationIntent;
import com.alexastudillo.partyregistry.application.NationalityView;
import com.alexastudillo.partyregistry.application.OutboxClaim;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.PageRequest;
import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.PartyCreationMutation;
import com.alexastudillo.partyregistry.application.PartyDetailsMutation;
import com.alexastudillo.partyregistry.application.PartyDetailsInput;
import com.alexastudillo.partyregistry.application.PartyDetailsView;
import com.alexastudillo.partyregistry.application.PartyIdentifierView;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import com.alexastudillo.partyregistry.application.PartyView;
import com.alexastudillo.partyregistry.application.ProtectedIdentifierData;
import com.alexastudillo.partyregistry.application.RecordedOutboxOutcome;
import com.alexastudillo.partyregistry.application.RecoveredOutboxEvent;
import com.alexastudillo.partyregistry.application.UpdatePartyCommand;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCatalogPort;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import com.alexastudillo.partyregistry.application.port.OutboxStorePort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.PartyStatus;
import com.alexastudillo.partyregistry.domain.PartyType;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Reactive PostgreSQL implementation of the Party Registry persistence boundary. */
public final class PostgreSqlPartyRegistryAdapter implements PartyQueryPort, PartyUnitOfWorkPort,
        IdentifierUnitOfWorkPort, IdentifierSchemeCatalogPort, OutboxStorePort {

    private static final String PARTY_COLUMNS =
            "id, tenant_id, type::text AS type, display_name, record_status::text AS record_status, "
                    + "created_at, created_by, updated_at, updated_by, version";
    private static final String IDENTIFIER_COLUMNS = """
            id, tenant_id, party_id, identifier_scheme_id, issuer_code, encrypted_value,
            normalized_value_hash, masked_value, encryption_key_version, normalization_version,
            is_primary, status::text AS status, issued_on, expires_on, verified_at, verified_by,
            created_at, created_by, updated_at, updated_by, version
            """;

    private final Pool pool;
    private final PostgreSqlAdapterSettings settings;
    private final PostgreSqlTransactionObserver transactionObserver;

    public PostgreSqlPartyRegistryAdapter(Pool pool, PostgreSqlAdapterSettings settings) {
        this(pool, settings, PostgreSqlTransactionObserver.noOp());
    }

    public PostgreSqlPartyRegistryAdapter(
            Pool pool, PostgreSqlAdapterSettings settings, PostgreSqlTransactionObserver transactionObserver) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.transactionObserver = Objects.requireNonNull(transactionObserver, "transactionObserver");
    }

    @Override
    public CompletionStage<PartyView> findByTenantAndId(UUID tenantId, UUID partyId) {
        return execute(pool.preparedQuery("SELECT " + PARTY_COLUMNS
                        + " FROM parties WHERE tenant_id = $1 AND id = $2")
                .execute(tuple(tenantId, partyId))
                .map(rows -> first(rows, PostgreSqlPartyRegistryAdapter::party)));
    }

    @Override
    public CompletionStage<PageResult<PartyView>> search(
            UUID tenantId, PartyType type, PartyStatus status, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        long offset = offset(pageRequest);
        String sql = "SELECT " + PARTY_COLUMNS + ", count(*) OVER() AS total_count FROM parties "
                + "WHERE tenant_id = $1 AND ($2::party_type IS NULL OR type = $2::party_type) "
                + "AND ($3::party_record_status IS NULL OR record_status = $3::party_record_status) "
                + "ORDER BY id ASC LIMIT $4 OFFSET $5";
        return execute(pool.preparedQuery(sql)
                .execute(tuple(tenantId, enumName(type), enumName(status), pageRequest.size(), offset))
                .map(rows -> page(rows, pageRequest, PostgreSqlPartyRegistryAdapter::party)));
    }

    @Override
    public CompletionStage<PartyDetailsView> findDetails(UUID tenantId, UUID partyId) {
        String sql = """
                SELECT d.party_id, d.kind, d.primary_name, d.secondary_name, d.optional_name,
                       d.country_code, d.start_date, d.end_date,
                       d.created_at, d.created_by, d.updated_at, d.updated_by
                  FROM (
                    SELECT party_id, 'NATURAL_PERSON' AS kind, given_names AS primary_name,
                           family_names AS secondary_name, preferred_name AS optional_name,
                           birth_country_code AS country_code, birth_date AS start_date,
                            date_of_death AS end_date, created_at, created_by, updated_at, updated_by
                      FROM natural_person_details
                    UNION ALL
                    SELECT party_id, 'LEGAL_ENTITY' AS kind, legal_name AS primary_name,
                           legal_form_code AS secondary_name, trade_name AS optional_name,
                           incorporation_country_code AS country_code, incorporated_on AS start_date,
                            dissolved_on AS end_date, created_at, created_by, updated_at, updated_by
                      FROM legal_entity_details
                  ) d
                  JOIN parties p ON p.id = d.party_id
                 WHERE p.tenant_id = $1 AND p.id = $2
                """;
        return execute(pool.preparedQuery(sql).execute(tuple(tenantId, partyId))
                .map(rows -> first(rows, PostgreSqlPartyRegistryAdapter::details)));
    }

    @Override
    public CompletionStage<NationalityView> findNationality(UUID tenantId, UUID partyId, UUID nationalityId) {
        String sql = """
                SELECT n.id, n.party_id, n.country_code, n.is_primary, n.valid_from, n.valid_until,
                       n.created_at, n.created_by, n.updated_at, n.updated_by
                  FROM party_nationalities n
                  JOIN parties p ON p.id = n.party_id
                 WHERE p.tenant_id = $1 AND p.id = $2 AND n.id = $3
                """;
        return execute(pool.preparedQuery(sql).execute(tuple(tenantId, partyId, nationalityId))
                .map(rows -> first(rows, PostgreSqlPartyRegistryAdapter::nationality)));
    }

    @Override
    public CompletionStage<PageResult<NationalityView>> searchNationalities(
            UUID tenantId,
            UUID partyId,
            String countryCode,
            Boolean primary,
            Boolean active,
            PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        String sql = """
                SELECT n.id, n.party_id, n.country_code, n.is_primary, n.valid_from, n.valid_until,
                       n.created_at, n.created_by, n.updated_at, n.updated_by,
                       count(*) OVER() AS total_count
                  FROM party_nationalities n
                  JOIN parties p ON p.id = n.party_id
                 WHERE p.tenant_id = $1 AND p.id = $2
                   AND ($3::char(2) IS NULL OR n.country_code = $3::char(2))
                   AND ($4::boolean IS NULL OR n.is_primary = $4)
                   AND ($5::boolean IS NULL OR (n.valid_until IS NULL) = $5)
                 ORDER BY n.id ASC LIMIT $6 OFFSET $7
                """;
        return execute(pool.preparedQuery(sql)
                .execute(tuple(tenantId, partyId, countryCode, primary, active, pageRequest.size(), offset(pageRequest)))
                .map(rows -> page(rows, pageRequest, PostgreSqlPartyRegistryAdapter::nationality)));
    }

    @Override
    public CompletionStage<MutationResult> createPartyAndAppendOutbox(PartyMutationIntent intent) {
        PartyCreationMutation mutation = requireMutation(intent, PartyCreationMutation.class);
        return transactional("party.create", connection -> insertParty(connection, intent, mutation)
                .compose(root -> insertDetails(connection, root.resourceId(), mutation.details(), intent.actorId())
                        .compose(ignored -> appendOutbox(connection, intent.tenantId(), "PARTY", root.resourceId(),
                                root.resourceId(), root.version(), intent.actorId(), intent.outbox(),
                                partyPayload(root.resourceId(), null)))))
                .thenApply(result -> result);
    }

    @Override
    public CompletionStage<MutationResult> updatePartyAndAppendOutbox(PartyMutationIntent intent) {
        UpdatePartyCommand mutation = requireMutation(intent, UpdatePartyCommand.class);
        if (mutation.displayName() == null || mutation.displayName().isBlank()) {
            return failed("VALIDATION_ERROR", "Party display name is required");
        }
        return mutatePartyRoot("party.update", intent,
                "UPDATE parties SET display_name = $4, updated_at = now(), updated_by = $5, version = version + 1 "
                        + "WHERE tenant_id = $1 AND id = $2 AND version = $3 RETURNING id, version",
                tuple(intent.tenantId(), intent.partyId(), intent.expectedVersion(), mutation.displayName(), intent.actorId()),
                partyPayload(intent.partyId(), null));
    }

    @Override
    public CompletionStage<MutationResult> transitionPartyAndAppendOutbox(PartyMutationIntent intent) {
        PartyStatus status = requireMutation(intent, PartyStatus.class);
        return mutatePartyRoot("party.transition", intent,
                "UPDATE parties SET record_status = $4::party_record_status, updated_at = now(), updated_by = $5, "
                        + "version = version + 1 WHERE tenant_id = $1 AND id = $2 AND version = $3 RETURNING id, version",
                tuple(intent.tenantId(), intent.partyId(), intent.expectedVersion(), status.name(), intent.actorId()),
                partyPayload(intent.partyId(), status));
    }

    @Override
    public CompletionStage<MutationResult> updateDetailsAndAppendOutbox(PartyMutationIntent intent) {
        PartyDetailsMutation mutation = requireMutation(intent, PartyDetailsMutation.class);
        return transactional("party.details.update", connection -> updatePartyVersion(
                        connection, intent.tenantId(), intent.partyId(), intent.expectedVersion(), intent.actorId(),
                        mutation.canonicalDisplayName())
                .compose(version -> updateDetails(connection, intent.partyId(), mutation.details(), intent.actorId())
                        .compose(updated -> requireOne(updated, "Party details"))
                        .compose(ignored -> appendOutbox(connection, intent.tenantId(), "PARTY", intent.partyId(),
                                intent.partyId(), version, intent.actorId(), intent.outbox(),
                                partyPayload(intent.partyId(), null)))));
    }

    @Override
    public CompletionStage<MutationResult> addNationalityAndAppendOutbox(NationalityMutationIntent intent) {
        if (intent.primary() == null) {
            return failed("VALIDATION_ERROR", "Nationality primary flag is required");
        }
        return transactional("party.nationality.add", connection -> updatePartyVersion(
                        connection, intent.tenantId(), intent.partyId(), intent.expectedVersion(), intent.actorId(), null)
                .compose(version -> connection.preparedQuery("""
                                INSERT INTO party_nationalities
                                    (id, party_id, country_code, is_primary, valid_from, valid_until,
                                     created_by, updated_by)
                                VALUES (COALESCE($1::uuid, uuidv7()), $2, $3, $4, $5, $6, $7, $7)
                                RETURNING id
                                """).execute(tuple(intent.nationalityId(), intent.partyId(), intent.countryCode(),
                                intent.primary(), intent.validFrom(), intent.validUntil(), intent.actorId()))
                        .map(rows -> rows.iterator().next().getUUID("id"))
                        .compose(nationalityId -> appendOutbox(connection, intent.tenantId(), "PARTY", intent.partyId(),
                                nationalityId, version, intent.actorId(), intent.outbox(),
                                nationalityPayload(intent.partyId(), nationalityId)))));
    }

    @Override
    public CompletionStage<MutationResult> updateNationalityAndAppendOutbox(NationalityMutationIntent intent) {
        return mutateNationality("party.nationality.update", intent, false);
    }

    @Override
    public CompletionStage<MutationResult> endNationalityAndAppendOutbox(NationalityMutationIntent intent) {
        return mutateNationality("party.nationality.end", intent, true);
    }

    CompletionStage<PartyIdentifierView> findIdentifierByTenantAndId(UUID tenantId, UUID identifierId) {
        return findIdentifier(tenantId, identifierId);
    }

    CompletionStage<PartyIdentifierView> findProtectedIdentifierByTenantAndId(UUID tenantId, UUID identifierId) {
        return findIdentifier(tenantId, identifierId);
    }

    CompletionStage<List<PartyIdentifierView>> findExactIdentifier(
            UUID tenantId, UUID schemeId, String normalizedValueHash) {
        String sql = "SELECT " + IDENTIFIER_COLUMNS + " FROM party_identifiers "
                + "WHERE tenant_id = $1 AND identifier_scheme_id = $2 AND normalized_value_hash = $3 ORDER BY id ASC LIMIT 1";
        return execute(pool.preparedQuery(sql).execute(tuple(tenantId, schemeId, normalizedValueHash))
                .map(rows -> mapRows(rows, PostgreSqlPartyRegistryAdapter::identifier)));
    }

    CompletionStage<PageResult<PartyIdentifierView>> searchIdentifiers(
            UUID tenantId,
            UUID partyId,
            UUID schemeId,
            PartyIdentifierStatus status,
            Boolean primary,
            PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        String sql = "SELECT " + IDENTIFIER_COLUMNS + ", count(*) OVER() AS total_count FROM party_identifiers "
                + "WHERE tenant_id = $1 AND ($2::uuid IS NULL OR party_id = $2) "
                + "AND ($3::uuid IS NULL OR identifier_scheme_id = $3) "
                + "AND ($4::party_identifier_status IS NULL OR status = $4::party_identifier_status) "
                + "AND ($5::boolean IS NULL OR is_primary = $5) ORDER BY id ASC LIMIT $6 OFFSET $7";
        return execute(pool.preparedQuery(sql)
                .execute(tuple(tenantId, partyId, schemeId, enumName(status), primary,
                        pageRequest.size(), offset(pageRequest)))
                .map(rows -> page(rows, pageRequest, PostgreSqlPartyRegistryAdapter::identifier)));
    }

    CompletionStage<List<PartyIdentifierView>> findIdentifiersByPartyAndScheme(
            UUID tenantId, UUID partyId, UUID schemeId, boolean verifiedPrimaryOnly) {
        String sql = "SELECT " + IDENTIFIER_COLUMNS + " FROM party_identifiers WHERE tenant_id = $1 "
                + "AND party_id = $2 AND identifier_scheme_id = $3 "
                + "AND (NOT $4::boolean OR (status = 'VERIFIED' AND is_primary = true)) "
                + "ORDER BY id ASC LIMIT $5";
        return execute(pool.preparedQuery(sql)
                .execute(tuple(tenantId, partyId, schemeId, verifiedPrimaryOnly, settings.maximumAssociationResults()))
                .map(rows -> mapRows(rows, PostgreSqlPartyRegistryAdapter::identifier)));
    }

    @Override
    public CompletionStage<MutationResult> createIdentifierAndAppendOutbox(IdentifierMutationIntent intent) {
        ProtectedIdentifierData protectedData = Objects.requireNonNull(intent.protectedIdentifier(), "protectedIdentifier");
        IdentifierCreateValues values = IdentifierCreateValues.from(intent.mutation(), protectedData);
        return transactional("identifier.create", connection -> connection.preparedQuery("""
                        INSERT INTO party_identifiers
                            (id, tenant_id, party_id, identifier_scheme_id, issuer_code, encrypted_value,
                             encryption_key_version, normalized_value_hash, masked_value, normalization_version,
                             is_primary, issued_on, expires_on, created_by, updated_by)
                        VALUES (COALESCE($1::uuid, uuidv7()), $2, $3, $4, $5, $6, $7, $8, $9, $10,
                                $11, $12, $13, $14, $14)
                        RETURNING id, version
                        """).execute(tuple(intent.identifierId(), intent.tenantId(), intent.partyId(), intent.schemeId(),
                        values.issuerCode(), protectedData.ciphertext(), protectedData.encryptionKeyVersion(),
                        protectedData.normalizedValueHash(), protectedData.maskedValue(), values.normalizationVersion(),
                        values.primary(), values.issuedOn(), values.expiresOn(), intent.actorId()))
                .map(rows -> resource(rows))
                .compose(resource -> appendOutbox(connection, intent.tenantId(), "PARTY_IDENTIFIER",
                        resource.resourceId(), resource.resourceId(), resource.version(), intent.actorId(), intent.outbox(),
                        identifierPayload(resource.resourceId(), intent.partyId(), intent.schemeId(), null))));
    }

    @Override
    public CompletionStage<MutationResult> updateIdentifierAndAppendOutbox(IdentifierMutationIntent intent) {
        IdentifierMutation.Update mutation = requireIdentifierMutation(intent, IdentifierMutation.Update.class);
        String sql = """
                UPDATE party_identifiers
                   SET issuer_code = $6, is_primary = $7, issued_on = $8, expires_on = $9,
                       updated_at = now(), updated_by = $10, version = version + 1
                 WHERE tenant_id = $1 AND id = $2 AND party_id = $3 AND identifier_scheme_id = $4 AND version = $5
                RETURNING id, party_id, identifier_scheme_id, version
                """;
        return mutateIdentifier("identifier.update", intent, sql,
                tuple(intent.tenantId(), intent.identifierId(), intent.partyId(), intent.schemeId(),
                        intent.expectedVersion(), mutation.issuerCode(), mutation.primary(), mutation.issuedOn(),
                        mutation.expiresOn(), intent.actorId()), null);
    }

    @Override
    public CompletionStage<MutationResult> transitionIdentifierAndAppendOutbox(IdentifierMutationIntent intent) {
        IdentifierMutation.Transition mutation = requireIdentifierMutation(intent, IdentifierMutation.Transition.class);
        String sql = """
                UPDATE party_identifiers
                   SET status = $6::party_identifier_status, verified_at = $7, verified_by = $8,
                       expires_on = $9, updated_at = now(), updated_by = $10, version = version + 1
                 WHERE tenant_id = $1 AND id = $2 AND party_id = $3 AND identifier_scheme_id = $4 AND version = $5
                RETURNING id, party_id, identifier_scheme_id, version
                """;
        return mutateIdentifier("identifier.transition", intent, sql,
                tuple(intent.tenantId(), intent.identifierId(), intent.partyId(), intent.schemeId(),
                        intent.expectedVersion(), mutation.status().name(), mutation.verifiedAt(), mutation.verifiedBy(),
                        mutation.expiresOn(), intent.actorId()), mutation.status());
    }

    @Override
    public CompletionStage<IdentifierSchemeMetadata> findUsableById(UUID schemeId) {
        String sql = """
                SELECT id, code, status::text AS status, applicable_subject_type::text AS applicable_subject_type,
                       normalizer_key, validator_key, minimum_length, maximum_length, requires_expiration
                  FROM identifier_schemes
                 WHERE id = $1 AND status = 'ACTIVE'
                """;
        return execute(pool.preparedQuery(sql).execute(tuple(schemeId))
                .map(rows -> first(rows, PostgreSqlPartyRegistryAdapter::scheme)));
    }

    @Override
    public CompletionStage<List<OutboxClaim>> claimEligible(int batchSize, Instant now) {
        if (batchSize < 1 || batchSize > settings.maximumOutboxBatchSize()) {
            return failed("VALIDATION_ERROR", "Outbox batch size exceeds the configured bound");
        }
        Objects.requireNonNull(now, "now");
        Instant visibleAgainAt = now.plus(settings.outboxClaimLease());
        String sql = """
                WITH candidates AS (
                    SELECT id FROM party_outbox_events
                     WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= $1)
                     ORDER BY COALESCE(next_attempt_at, created_at), created_at, id
                     FOR UPDATE SKIP LOCKED
                     LIMIT $2
                )
                UPDATE party_outbox_events e
                   SET next_attempt_at = $3, last_attempt_at = $1,
                       publish_attempts = e.publish_attempts + 1,
                       updated_at = $1, updated_by = $4, version = e.version + 1
                  FROM candidates c
                 WHERE e.id = c.id
                RETURNING e.id, e.tenant_id, e.version, e.event_type, e.event_schema_version,
                          e.payload, e.occurred_at
                """;
        return transactional("outbox.claim", connection -> connection.preparedQuery(sql)
                .execute(tuple(now, batchSize, visibleAgainAt, settings.publisherActorId()))
                .map(rows -> mapRows(rows, PostgreSqlPartyRegistryAdapter::claim)));
    }

    @Override
    public CompletionStage<Boolean> recordOutcome(
            UUID eventId, long claimedVersion, RecordedOutboxOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        String errorCode = outcome == RecordedOutboxOutcome.FAILED ? "NON_RECOVERABLE_PUBLICATION" : null;
        String sql = """
                UPDATE party_outbox_events
                   SET status = $3::outbox_status,
                       published_at = CASE WHEN $3 = 'PUBLISHED' THEN now() ELSE NULL END,
                       next_attempt_at = CASE WHEN $3 = 'PENDING' THEN next_attempt_at ELSE NULL END,
                       last_error_code = $4, last_error_detail = NULL,
                       updated_at = now(), updated_by = $5, version = version + 1
                 WHERE id = $1 AND version = $2
                """;
        return transactional("outbox.outcome", connection -> connection.preparedQuery(sql)
                .execute(tuple(eventId, claimedVersion, outcome.name(), errorCode, settings.publisherActorId()))
                .map(rows -> rows.rowCount() == 1));
    }

    @Override
    public CompletionStage<RecoveredOutboxEvent> recoverFailed(UUID eventId, long expectedVersion) {
        String sql = """
                UPDATE party_outbox_events
                   SET status = 'PENDING', next_attempt_at = NULL, last_error_code = NULL,
                       last_error_detail = NULL, published_at = NULL,
                       updated_at = now(), updated_by = $3, version = version + 1
                 WHERE id = $1 AND version = $2 AND status = 'FAILED'
                RETURNING id, version, event_type, payload
                """;
        return transactional("outbox.recover", connection -> connection.preparedQuery(sql)
                .execute(tuple(eventId, expectedVersion, settings.publisherActorId()))
                .map(rows -> first(rows, row -> new RecoveredOutboxEvent(
                        row.getUUID("id"), number(row, "version").longValue(), row.getString("event_type"),
                        json(row, "payload")))));
    }

    private CompletionStage<PartyIdentifierView> findIdentifier(UUID tenantId, UUID identifierId) {
        String sql = "SELECT " + IDENTIFIER_COLUMNS
                + " FROM party_identifiers WHERE tenant_id = $1 AND id = $2";
        return execute(pool.preparedQuery(sql).execute(tuple(tenantId, identifierId))
                .map(rows -> first(rows, PostgreSqlPartyRegistryAdapter::identifier)));
    }

    private CompletionStage<MutationResult> mutatePartyRoot(
            String operation, PartyMutationIntent intent, String sql, Tuple parameters, JsonObject payload) {
        return transactional(operation, connection -> connection.preparedQuery(sql).execute(parameters)
                .compose(rows -> mutationOrClassify(connection, rows, intent.tenantId(), intent.partyId()))
                .compose(resource -> appendOutbox(connection, intent.tenantId(), "PARTY", resource.resourceId(),
                        resource.resourceId(), resource.version(), intent.actorId(), intent.outbox(), payload)));
    }

    private Future<VersionedResource> insertParty(
            SqlConnection connection, PartyMutationIntent intent, PartyCreationMutation mutation) {
        String sql = """
                INSERT INTO parties (id, tenant_id, type, display_name, created_by, updated_by)
                VALUES (COALESCE($1::uuid, uuidv7()), $2, $3::party_type, $4, $5, $5)
                RETURNING id, version
                """;
        return connection.preparedQuery(sql).execute(tuple(intent.partyId(), intent.tenantId(), mutation.type().name(),
                        mutation.details().canonicalDisplayName(), intent.actorId()))
                .map(PostgreSqlPartyRegistryAdapter::resource);
    }

    private Future<Void> insertDetails(
            SqlConnection connection, UUID partyId, PartyDetailsMutation mutation, String actorId) {
        PartyDetailsInput value = mutation.details();
        if (value.kind() == DetailKind.NATURAL_PERSON) {
            return connection.preparedQuery("""
                            INSERT INTO natural_person_details
                                (party_id, given_names, family_names, preferred_name, birth_date, date_of_death,
                                 birth_country_code, created_by, updated_by)
                            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $8)
                            """).execute(tuple(partyId, value.primaryName(), value.secondaryName(), value.optionalName(),
                            value.startDate(), value.endDate(), value.countryCode(), actorId))
                    .mapEmpty();
        }
        return connection.preparedQuery("""
                        INSERT INTO legal_entity_details
                            (party_id, legal_name, trade_name, legal_form_code, incorporation_country_code,
                             incorporated_on, dissolved_on, created_by, updated_by)
                        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $8)
                        """).execute(tuple(partyId, value.primaryName(), value.optionalName(), value.secondaryName(),
                        value.countryCode(), value.startDate(), value.endDate(), actorId))
                .mapEmpty();
    }

    private Future<Integer> updateDetails(
            SqlConnection connection, UUID partyId, PartyDetailsInput value, String actorId) {
        if (value.kind() == DetailKind.NATURAL_PERSON) {
            return connection.preparedQuery("""
                            UPDATE natural_person_details
                               SET given_names = $2, family_names = $3, preferred_name = $4,
                                   birth_date = $5, date_of_death = $6, birth_country_code = $7,
                                   updated_at = now(), updated_by = $8
                             WHERE party_id = $1
                            """).execute(tuple(partyId, value.primaryName(), value.secondaryName(), value.optionalName(),
                            value.startDate(), value.endDate(), value.countryCode(), actorId))
                    .map(RowSet::rowCount);
        }
        return connection.preparedQuery("""
                        UPDATE legal_entity_details
                           SET legal_name = $2, trade_name = $3, legal_form_code = $4,
                               incorporation_country_code = $5, incorporated_on = $6, dissolved_on = $7,
                               updated_at = now(), updated_by = $8
                         WHERE party_id = $1
                        """).execute(tuple(partyId, value.primaryName(), value.optionalName(), value.secondaryName(),
                        value.countryCode(), value.startDate(), value.endDate(), actorId))
                .map(RowSet::rowCount);
    }

    private CompletionStage<MutationResult> mutateNationality(
            String operation, NationalityMutationIntent intent, boolean ending) {
        return transactional(operation, connection -> updatePartyVersion(
                        connection, intent.tenantId(), intent.partyId(), intent.expectedVersion(), intent.actorId(), null)
                .compose(version -> {
                    String sql = ending ? """
                            UPDATE party_nationalities
                               SET valid_until = $4, updated_at = now(), updated_by = $5
                             WHERE id = $1 AND party_id = $2 AND country_code = $3
                            """ : """
                            UPDATE party_nationalities
                               SET country_code = $3, is_primary = COALESCE($4, is_primary),
                                   valid_from = $5, valid_until = $6, updated_at = now(), updated_by = $7
                             WHERE id = $1 AND party_id = $2
                            """;
                    Tuple values = ending
                            ? tuple(intent.nationalityId(), intent.partyId(), intent.countryCode(),
                                    intent.validUntil(), intent.actorId())
                            : tuple(intent.nationalityId(), intent.partyId(), intent.countryCode(), intent.primary(),
                                    intent.validFrom(), intent.validUntil(), intent.actorId());
                    return connection.preparedQuery(sql).execute(values)
                            .map(RowSet::rowCount)
                            .compose(updated -> requireOne(updated, "Nationality"))
                            .compose(ignored -> appendOutbox(connection, intent.tenantId(), "PARTY", intent.partyId(),
                                    intent.nationalityId(), version, intent.actorId(), intent.outbox(),
                                    nationalityPayload(intent.partyId(), intent.nationalityId())));
                }));
    }

    private CompletionStage<MutationResult> mutateIdentifier(
            String operation,
            IdentifierMutationIntent intent,
            String sql,
            Tuple parameters,
            PartyIdentifierStatus status) {
        return transactional(operation, connection -> connection.preparedQuery(sql).execute(parameters)
                .compose(rows -> identifierMutationOrClassify(connection, rows, intent.tenantId(), intent.identifierId()))
                .compose(resource -> appendOutbox(connection, intent.tenantId(), "PARTY_IDENTIFIER",
                        resource.resourceId(), resource.resourceId(), resource.version(), intent.actorId(), intent.outbox(),
                        identifierPayload(resource.resourceId(), resource.partyId(), resource.schemeId(), status))));
    }

    private Future<Long> updatePartyVersion(
            SqlConnection connection,
            UUID tenantId,
            UUID partyId,
            long expectedVersion,
            String actorId,
            String displayName) {
        String sql = """
                UPDATE parties
                   SET display_name = COALESCE($4, display_name), updated_at = now(), updated_by = $5,
                       version = version + 1
                 WHERE tenant_id = $1 AND id = $2 AND version = $3
                RETURNING version
                """;
        return connection.preparedQuery(sql).execute(tuple(tenantId, partyId, expectedVersion, displayName, actorId))
                .compose(rows -> {
                    if (rows.size() == 1) {
                        return Future.succeededFuture(number(rows.iterator().next(), "version").longValue());
                    }
                    return classifyPartyMiss(connection, tenantId, partyId);
                });
    }

    private Future<VersionedResource> mutationOrClassify(
            SqlConnection connection, RowSet<Row> rows, UUID tenantId, UUID partyId) {
        if (rows.size() == 1) {
            return Future.succeededFuture(resource(rows));
        }
        return classifyPartyMiss(connection, tenantId, partyId).map(ignored -> null);
    }

    private Future<Long> classifyPartyMiss(SqlConnection connection, UUID tenantId, UUID partyId) {
        return connection.preparedQuery("SELECT version FROM parties WHERE tenant_id = $1 AND id = $2")
                .execute(tuple(tenantId, partyId))
                .compose(rows -> rows.size() == 0
                        ? Future.failedFuture(new ApplicationFailure("NOT_FOUND", "Party was not found"))
                        : Future.failedFuture(new ApplicationFailure("VERSION_CONFLICT", "Party version is stale")));
    }

    private Future<IdentifierResource> identifierMutationOrClassify(
            SqlConnection connection, RowSet<Row> rows, UUID tenantId, UUID identifierId) {
        if (rows.size() == 1) {
            Row row = rows.iterator().next();
            return Future.succeededFuture(new IdentifierResource(row.getUUID("id"), row.getUUID("party_id"),
                    row.getUUID("identifier_scheme_id"), number(row, "version").longValue()));
        }
        return connection.preparedQuery("SELECT version FROM party_identifiers WHERE tenant_id = $1 AND id = $2")
                .execute(tuple(tenantId, identifierId))
                .compose(found -> found.size() == 0
                        ? Future.failedFuture(new ApplicationFailure("NOT_FOUND", "Party Identifier was not found"))
                        : Future.failedFuture(new ApplicationFailure(
                                "VERSION_CONFLICT", "Party Identifier version or immutable association is stale")));
    }

    private Future<MutationResult> appendOutbox(
            SqlConnection connection,
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            UUID resultResourceId,
            long aggregateVersion,
            String actorId,
            OutboxIntent intent,
            JsonObject payload) {
        String sql = """
                INSERT INTO party_outbox_events
                    (tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type,
                     event_schema_version, payload, correlation_id, created_by, updated_by)
                VALUES ($1, $2::party_outbox_aggregate_type, $3, $4, $5, 1, $6, $7, $8, $8)
                RETURNING id, occurred_at
                """;
        return connection.preparedQuery(sql).execute(tuple(tenantId, aggregateType, aggregateId, aggregateVersion,
                        intent.eventType(), payload, intent.correlationId().toString(), actorId))
                .map(rows -> {
                    Row row = rows.iterator().next();
                    return new MutationResult(resultResourceId, aggregateVersion, row.getUUID("id"),
                            instant(row, "occurred_at"));
                });
    }

    private <T> CompletionStage<T> transactional(
            String operation, Function<SqlConnection, Future<T>> work) {
        AtomicBoolean opened = new AtomicBoolean();
        Future<T> transaction = pool.withTransaction(connection -> {
            transactionObserver.opened(operation);
            opened.set(true);
            return work.apply(connection);
        });
        return execute(transaction.andThen(ignored -> {
            if (opened.get()) {
                transactionObserver.completed(operation);
            }
        }));
    }

    private static <T> CompletionStage<T> execute(Future<T> operation) {
        return operation.recover(failure -> Future.failedFuture(PostgreSqlErrorMapper.map(failure)))
                .toCompletionStage();
    }

    private static <T> CompletionStage<T> failed(String code, String message) {
        return Future.<T>failedFuture(new ApplicationFailure(code, message)).toCompletionStage();
    }

    private static Future<Void> requireOne(int updated, String resource) {
        return updated == 1
                ? Future.succeededFuture()
                : Future.failedFuture(new ApplicationFailure("NOT_FOUND", resource + " was not found"));
    }

    private static <T> T requireMutation(PartyMutationIntent intent, Class<T> type) {
        Objects.requireNonNull(intent, "intent");
        if (!type.isInstance(intent.mutation())) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Unsupported Party mutation");
        }
        return type.cast(intent.mutation());
    }

    private static <T extends IdentifierMutation> T requireIdentifierMutation(
            IdentifierMutationIntent intent, Class<T> type) {
        Objects.requireNonNull(intent, "intent");
        if (!type.isInstance(intent.mutation())) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Unsupported Party Identifier mutation");
        }
        return type.cast(intent.mutation());
    }

    private static PartyView party(Row row) {
        return new PartyView(row.getUUID("id"), row.getUUID("tenant_id"),
                PartyType.valueOf(row.getString("type")), row.getString("display_name"),
                PartyStatus.valueOf(row.getString("record_status")), number(row, "version").longValue(), audit(row));
    }

    private static PartyDetailsView details(Row row) {
        return new PartyDetailsView(row.getUUID("party_id"), DetailKind.valueOf(row.getString("kind")),
                row.getString("primary_name"), row.getString("secondary_name"), row.getString("optional_name"),
                row.getString("country_code"), row.getLocalDate("start_date"), row.getLocalDate("end_date"),
                audit(row));
    }

    private static NationalityView nationality(Row row) {
        return new NationalityView(row.getUUID("id"), row.getUUID("party_id"), row.getString("country_code"),
                row.getBoolean("is_primary"), row.getLocalDate("valid_until") == null,
                row.getLocalDate("valid_from"), row.getLocalDate("valid_until"), audit(row));
    }

    private static AuditFacts audit(Row row) {
        return new AuditFacts(instant(row, "created_at"), row.getString("created_by"),
                instant(row, "updated_at"), row.getString("updated_by"));
    }

    private static PartyIdentifierView identifier(Row row) {
        return new PartyIdentifierView(row.getUUID("id"), row.getUUID("tenant_id"), row.getUUID("party_id"),
                row.getUUID("identifier_scheme_id"), row.getString("issuer_code"), row.getString("encrypted_value"),
                row.getString("normalized_value_hash"), row.getString("masked_value"),
                number(row, "encryption_key_version").intValue(), number(row, "normalization_version").intValue(),
                row.getBoolean("is_primary"), PartyIdentifierStatus.valueOf(row.getString("status")),
                row.getLocalDate("issued_on"), row.getLocalDate("expires_on"), instantOrNull(row, "verified_at"),
                row.getString("verified_by"), instant(row, "created_at"), row.getString("created_by"),
                instant(row, "updated_at"), row.getString("updated_by"), number(row, "version").longValue());
    }

    private static IdentifierSchemeMetadata scheme(Row row) {
        return new IdentifierSchemeMetadata(row.getUUID("id"), row.getString("code"), row.getString("status"),
                row.getString("applicable_subject_type"), row.getString("normalizer_key"),
                row.getString("validator_key"), nullableInteger(row, "minimum_length"),
                nullableInteger(row, "maximum_length"), row.getBoolean("requires_expiration"));
    }

    private static OutboxClaim claim(Row row) {
        return new OutboxClaim(row.getUUID("id"), row.getUUID("tenant_id"), number(row, "version").longValue(),
                row.getString("event_type"), number(row, "event_schema_version").intValue(), json(row, "payload"),
                instant(row, "occurred_at"));
    }

    private static VersionedResource resource(RowSet<Row> rows) {
        Row row = rows.iterator().next();
        return new VersionedResource(row.getUUID("id"), number(row, "version").longValue());
    }

    private static <T> T first(RowSet<Row> rows, Function<Row, T> mapper) {
        return rows.size() == 0 ? null : mapper.apply(rows.iterator().next());
    }

    private static <T> List<T> mapRows(RowSet<Row> rows, Function<Row, T> mapper) {
        List<T> values = new ArrayList<>(rows.size());
        rows.forEach(row -> values.add(mapper.apply(row)));
        return List.copyOf(values);
    }

    private static <T> PageResult<T> page(
            RowSet<Row> rows, PageRequest request, Function<Row, T> mapper) {
        List<T> values = mapRows(rows, mapper);
        long total = rows.size() == 0 ? 0 : number(rows.iterator().next(), "total_count").longValue();
        return new PageResult<>(values, request.page(), request.size(), total);
    }

    private static long offset(PageRequest request) {
        try {
            return Math.multiplyExact((long) request.page(), request.size());
        } catch (ArithmeticException failure) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Page offset is too large");
        }
    }

    private static Tuple tuple(Object... values) {
        Tuple tuple = Tuple.tuple();
        for (Object value : values) {
            tuple.addValue(value);
        }
        return tuple;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Number number(Row row, String column) {
        return (Number) row.getValue(column);
    }

    private static Integer nullableInteger(Row row, String column) {
        Number value = (Number) row.getValue(column);
        return value == null ? null : value.intValue();
    }

    private static Instant instant(Row row, String column) {
        Object value = row.getValue(column);
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant timestamp) {
            return timestamp;
        }
        return ((java.time.LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }

    private static Instant instantOrNull(Row row, String column) {
        return row.getValue(column) == null ? null : instant(row, column);
    }

    private static String json(Row row, String column) {
        Object value = row.getValue(column);
        return value instanceof JsonObject object ? object.encode() : String.valueOf(value);
    }

    private static JsonObject partyPayload(UUID partyId, PartyStatus status) {
        JsonObject payload = new JsonObject().put("partyId", partyId.toString());
        return status == null ? payload : payload.put("status", status.name());
    }

    private static JsonObject nationalityPayload(UUID partyId, UUID nationalityId) {
        return new JsonObject().put("partyId", partyId.toString())
                .put("nationalityId", nationalityId.toString());
    }

    private static JsonObject identifierPayload(
            UUID identifierId, UUID partyId, UUID schemeId, PartyIdentifierStatus status) {
        JsonObject payload = new JsonObject()
                .put("partyIdentifierId", identifierId.toString())
                .put("partyId", partyId.toString())
                .put("identifierSchemeId", schemeId.toString());
        return status == null ? payload : payload.put("status", status.name());
    }

    private record VersionedResource(UUID resourceId, long version) {}

    private record IdentifierResource(UUID resourceId, UUID partyId, UUID schemeId, long version) {}

    private record IdentifierCreateValues(
            String issuerCode, boolean primary, LocalDate issuedOn, LocalDate expiresOn, int normalizationVersion) {
        private static IdentifierCreateValues from(IdentifierMutation mutation, ProtectedIdentifierData protectedData) {
            if (mutation instanceof IdentifierMutation.Creation creation) {
                return new IdentifierCreateValues(creation.issuerCode(), creation.primary(), creation.issuedOn(),
                        creation.expiresOn(), creation.normalizationVersion());
            }
            if (mutation instanceof IdentifierMutation.LegacyCreation) {
                return new IdentifierCreateValues(null, false, null, null, protectedData.normalizationVersion());
            }
            throw new ApplicationFailure("VALIDATION_ERROR", "Unsupported Party Identifier creation mutation");
        }
    }
}
