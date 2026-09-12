package com.alexastudillo.partyregistry;

import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the deterministic test-only identifier-scheme catalog and its profile scope.
 */
class IdentifierSchemeTestFixtureTest {

    private static final String V1 = "V1__create_party_registry_schema.sql";
    private static final String V2 = "V2__create_api_idempotency_records.sql";
    private static final String V3 = "V3__seed_ecuador_identifier_schemes.sql";
    private static final String V4 = "V4__make_identifier_expiration_optional.sql";
    private static final String V1000 = "V1000__seed_identifier_scheme_test_fixtures.sql";
    private static final String V1_SHA_256 =
            "7da1a66b7cddb2389da0032c5f3ccef8049e75eaa77783eda37e89758235e4d7";
    private static final String V2_SHA_256 =
            "9dd1164d81535fcebd9b76851e910264a116c8fbf37acdd05a57960f40b3e723";
    private static final String V3_SHA_256 =
            "8ad6ac7d0b7d461c617d5ad48e9ed9f0fe3fab25b2019c0ef22b4cc6a9f02d22";
    private static final Path PRODUCTION_MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path TEST_MIGRATIONS = Path.of("src/test/resources/db/test-migration");
    private static final String TEST_MIGRATION_RESOURCE = "/db/test-migration/" + V1000;

    @Test
    void exposesEveryRequiredSchemeCaseThroughStableCodes() throws IOException {
        String migration = resourceText(TEST_MIGRATION_RESOURCE);

        IdentifierSchemeTestFixtures.ALL_CODES.forEach(code -> assertTrue(migration.contains("'" + code + "'")));
        assertTrue(migration.contains("'NATURAL_PERSON'"));
        assertTrue(migration.contains("'LEGAL_ENTITY'"));
        assertTrue(migration.contains("'ACTIVE'"));
        assertTrue(migration.contains("'DRAFT'"));
        assertTrue(migration.contains("'DEPRECATED'"));
        assertTrue(migration.contains("'RETIRED'"));
        assertTrue(migration.contains("true,\n        'ACTIVE'"));
    }

    @Test
    void preservesImmutableProductionMigrationsAndIsolatesTheOnlyFixtureMigration()
            throws IOException, NoSuchAlgorithmException {
        Map<String, Path> productionMigrations = migrationFiles(PRODUCTION_MIGRATIONS);
        Map<String, Path> testMigrations = migrationFiles(TEST_MIGRATIONS);

        assertEquals(Set.of(V1, V2, V3, V4), productionMigrations.keySet());
        assertEquals(Set.of(V1000), testMigrations.keySet());
        assertEquals(V1_SHA_256, sha256(productionMigrations.get(V1)));
        assertEquals(V2_SHA_256, sha256(productionMigrations.get(V2)));
        assertEquals(V3_SHA_256, sha256(productionMigrations.get(V3)));
    }

    @Test
    void keepsFlywayAsSchemaAuthorityAndLoadsFixturesOnlyInTheTestProfile() throws IOException {
        Properties properties = new Properties();
        try (InputStream configuration = resource("/application.properties")) {
            properties.load(configuration);
        }

        assertEquals("db/migration", properties.getProperty("quarkus.flyway.locations"));
        assertEquals(
                "db/migration,db/test-migration",
                properties.getProperty("%test.quarkus.flyway.locations"));
        assertEquals(
                "validate",
                properties.getProperty("quarkus.hibernate-orm.schema-management.strategy"));
        assertFalse(properties.containsKey("quarkus.hibernate-orm.database.generation"));
        assertFalse(properties.containsKey("jakarta.persistence.schema-generation.database.action"));
        assertEquals("true", properties.getProperty("quarkus.flyway.migrate-at-start"));
        assertEquals("true", properties.getProperty("quarkus.flyway.validate-on-migrate"));
        assertEquals("true", properties.getProperty("quarkus.flyway.clean-disabled"));
    }

    @Test
    void keepsTestFixtureCodesOutOfProductionMigrations() throws IOException {
        StringBuilder productionMigrations = new StringBuilder();
        for (Path migration : migrationFiles(PRODUCTION_MIGRATIONS).values()) {
            productionMigrations.append(Files.readString(migration, StandardCharsets.UTF_8));
        }

        IdentifierSchemeTestFixtures.ALL_CODES
                .forEach(code -> assertFalse(productionMigrations.toString().contains(code)));
    }

    private static Map<String, Path> migrationFiles(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory), () -> "Missing migration directory: " + directory);
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .collect(Collectors.toUnmodifiableMap(
                            path -> path.getFileName().toString(),
                            Function.identity()));
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String resourceText(String path) throws IOException {
        try (InputStream input = resource(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream resource(String path) {
        InputStream input = IdentifierSchemeTestFixtureTest.class.getResourceAsStream(path);
        assertNotNull(input, () -> "Missing test resource: " + path);
        return input;
    }
}
