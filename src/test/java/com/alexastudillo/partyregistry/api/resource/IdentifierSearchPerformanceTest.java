package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSearchContractAssertions;
import com.alexastudillo.partyregistry.domain.model.*;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.support.IdentifierSearchDatabaseFixture;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Measures real protected workloads and sanitized query plans without claiming an SLA or exporting query predicates. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchPerformanceTest {
    @Inject DataSource dataSource;
    @Inject JcaIdentifierProtectionAdapter protection;
    @Inject MeterRegistry registry;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void measuresDenseSparseAndNoMatchTraversalAtRepresentativeQualifiedVolumes() throws Exception {
        var samples = new ArrayList<WorkSample>();
        var plans = new ArrayList<QueryPlan>();
        for (int volume : List.of(512, 4096)) {
            UUID tenant = UUID.randomUUID();
            String code = "MEASURE_" + UUID.randomUUID();
            UUID scheme;
            var ids = new ArrayList<UUID>();
            try (var connection = dataSource.getConnection()) {
                var fixture = new IdentifierSearchDatabaseFixture(connection, protection);
                scheme = fixture.scheme(code, IdentifierSchemeStatus.RETIRED, "TRIM_UPPERCASE_V1");
                UUID party = fixture.party(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
                for (int index = 0; index < volume; index++) {
                    String value = index < 201 ? "DENSE-SAME" : (index % 256 == 0 ? "DENSE-SPARSE-" : "DENSE-") + index;
                    ids.add(fixture.identifier(tenant, party, scheme, UUID.randomUUID(), value, PartyIdentifierStatus.REVOKED,
                            false, Instant.EPOCH.plusSeconds(index)));
                }
                plans.add(explain(connection, tenant, scheme, volume, "exact-count", true, false, null));
                plans.add(explain(connection, tenant, scheme, volume, "prefix-first-batch", false, false, null));
                plans.add(explain(connection, tenant, scheme, volume, "prefix-keyset-batch", false, true, ids.get(volume - 256)));
            }
            samples.add(measure(tenant, code, volume, false, "exact", "DENSE-SAME", 201));
            samples.add(measure(tenant, code, volume, true, "dense", "DENSE", volume));
            samples.add(measure(tenant, code, volume, true, "sparse", "DENSE-SPARSE", (volume - 1) / 256));
            samples.add(measure(tenant, code, volume, true, "no-match", "ABSENT", 0));
        }
        var report = Path.of("build/reports/identifier-search/performance.json");
        Files.createDirectories(report.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), Map.of("processors", Runtime.getRuntime().availableProcessors(),
                "testHeapBytes", Runtime.getRuntime().maxMemory(), "samples", samples, "plans", plans));
    }

    private WorkSample measure(UUID tenant, String code, int volume, boolean prefix, String workload, String selector, int matches) {
        String operation = prefix ? "prefix" : "exact";
        var measurements = List.of("sql_statements", "candidate_batches", "candidates", "matches", "max_batch", "max_page");
        var before = new java.util.HashMap<String, Double>();
        measurements.forEach(measurement -> before.put(measurement, total(operation, measurement)));
        long started = System.nanoTime();
        var response = given().contentType("application/json").header("Tenant-Id", tenant).header("Process-Id", UUID.randomUUID())
                .header("User-Id", "search-measurement-test").body(Map.of("identifierSchemeCode", code, prefix ? "prefix" : "value", selector))
                .post(prefix ? IdentifierSearchContractAssertions.PREFIX_PATH : IdentifierSearchContractAssertions.EXACT_PATH);
        long elapsed = System.nanoTime() - started;
        assertEquals(200, response.statusCode());
        assertEquals(matches, response.jsonPath().getInt("totalElements"));
        var work = new java.util.HashMap<String, Long>();
        measurements.forEach(measurement -> work.put(measurement, Math.round(total(operation, measurement) - before.get(measurement))));
        assertEquals(matches, work.get("matches"));
        if (prefix) {
            assertEquals(volume, work.get("candidates"));
            assertTrue(work.get("max_batch") <= 256);
            assertTrue(work.get("max_page") <= 50);
            assertEquals(volume / 256 + 1, work.get("candidate_batches"));
        } else {
            assertEquals(0, work.get("candidates"));
            assertEquals(0, work.get("candidate_batches"));
        }
        return new WorkSample(volume, workload, elapsed / 1_000_000.0, response.statusCode(), Map.copyOf(work),
                gauge("workers"), gauge("capacity"), gauge("active"), gauge("queued"),
                registry.get("postgresql.queue.size").gauge().value());
    }

    private double total(String operation, String measurement) {
        var summary = registry.find("party.registry.identifier.search.work")
                .tags("operation", operation, "measurement", measurement, "outcome", "success").summary();
        return summary == null ? 0 : summary.totalAmount();
    }

    private double gauge(String measurement) {
        return registry.get("party.registry.identifier.search.cpu." + measurement).gauge().value();
    }

    private QueryPlan explain(Connection connection, UUID tenant, UUID scheme, int volume, String workload,
            boolean count, boolean keyset, UUID boundary) throws Exception {
        String select = count ? "count(i.id)" : "p.id, p.type, p.display_name, p.record_status, s.code, i.masked_value, i.status, i.is_primary, i.created_at, i.id, i.encrypted_value, i.encryption_key_version, i.normalization_version";
        String sql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) select " + select + """
                 from party_identifiers i join parties p on p.tenant_id = i.tenant_id and p.id = i.party_id
                 join identifier_schemes s on s.id = i.identifier_scheme_id
                 where i.tenant_id = ? and p.tenant_id = ? and i.identifier_scheme_id = ?
                 and i.status in ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'EXPIRED', 'REVOKED')
                 and p.record_status in ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')
                """ + (count ? " and i.normalized_value_hash = ?" : "")
                + (keyset ? " and (i.created_at < ? or (i.created_at = ? and i.id < ?))" : "")
                + (count ? "" : " order by i.created_at desc, i.id desc limit 256");
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenant);
            statement.setObject(2, tenant);
            statement.setObject(3, scheme);
            if (count) statement.setString(4, protection.lookupHash(new TenantId(tenant), "DENSE-SAME").orElseThrow());
            if (keyset) {
                statement.setTimestamp(4, Timestamp.from(Instant.EPOCH.plusSeconds(volume - 256)));
                statement.setTimestamp(5, Timestamp.from(Instant.EPOCH.plusSeconds(volume - 256)));
                statement.setObject(6, boundary);
            }
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                JsonNode plan = json.readTree(result.getString(1)).get(0);
                var nodes = new ArrayList<PlanNode>();
                collectPlan(plan.path("Plan"), nodes);
                return new QueryPlan(volume, workload, plan.path("Planning Time").asDouble(), plan.path("Execution Time").asDouble(), List.copyOf(nodes));
            }
        }
    }

    private static void collectPlan(JsonNode node, List<PlanNode> nodes) {
        nodes.add(new PlanNode(node.path("Node Type").asText(), node.path("Index Name").asText(""), node.path("Actual Rows").asLong(),
                node.path("Shared Hit Blocks").asLong(), node.path("Shared Read Blocks").asLong()));
        node.path("Plans").forEach(child -> collectPlan(child, nodes));
    }

    /** Records numeric HTTP work/capacity evidence with a fixed synthetic workload label and no scope identities. */
    private record WorkSample(int candidatesInScope, String workload, double httpMillis, int status, Map<String, Long> work,
            double cpuWorkers, double cpuCapacity, double cpuActiveAfter, double cpuQueuedAfter, double poolQueuedAfter) { }

    /** Retains only sanitized plan timings and numeric node evidence, never predicates or private projection values. */
    private record QueryPlan(int candidatesInScope, String workload, double planningMillis, double executionMillis, List<PlanNode> nodes) { }

    /** Exposes actual plan/operator/index choices without tenant, selector, lookup hash or encrypted-value fields. */
    private record PlanNode(String operator, String index, long actualRows, long sharedHitBlocks, long sharedReadBlocks) { }
}
