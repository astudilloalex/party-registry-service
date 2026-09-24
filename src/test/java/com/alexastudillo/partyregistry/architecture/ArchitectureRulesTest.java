package com.alexastudillo.partyregistry.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves each architecture rule detects a representative forbidden dependency.
 */
class ArchitectureRulesTest {

    private static final String FIXTURE_ROOT = "com.alexastudillo.partyregistry.architecture.fixture";

    @Test
    void rejectsApiDependingOnInfrastructure() {
        assertViolation(
                ArchitectureRules.layerDependenciesPointInward(FIXTURE_ROOT + ".direction"),
                importFixtures(FIXTURE_ROOT + ".direction"),
                "ApiDependingOnInfrastructure");
    }

    @Test
    void rejectsDomainDependingOnMutiny() {
        assertViolation(
                ArchitectureRules.domainIsFrameworkIndependent(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.domain"),
                "DomainDependingOnMutiny",
                "io.smallrye.mutiny.Uni");
    }

    @Test
    void rejectsDomainDependingOnQuarkus() {
        assertViolation(
                ArchitectureRules.domainIsFrameworkIndependent(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.domain"),
                "DomainDependingOnQuarkus",
                "io.quarkus.runtime.StartupEvent");
    }

    @Test
    void rejectsDomainDependingOnPersistenceFrameworks() {
        assertViolation(
                ArchitectureRules.domainIsFrameworkIndependent(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.domain"),
                "DomainDependingOnPersistence",
                "io.quarkus.hibernate.reactive.panache.PanacheEntity",
                "jakarta.persistence.Entity",
                "org.hibernate.Session");
    }

    @Test
    void rejectsDomainDependingOnJackson() {
        assertViolation(
                ArchitectureRules.domainIsFrameworkIndependent(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.domain"),
                "DomainDependingOnJackson",
                "com.fasterxml.jackson.databind.ObjectMapper");
    }

    @Test
    void rejectsDomainDependingOnHttpContracts() {
        assertViolation(
                ArchitectureRules.domainIsFrameworkIndependent(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.domain"),
                "DomainDependingOnHttpContracts",
                "com.alexastudillo.api.response.contract.ApiResponse",
                "jakarta.ws.rs.core.Response",
                "java.net.http.HttpResponse",
                "org.jboss.resteasy.reactive.RestResponse");
    }

    @Test
    void rejectsDomainDependingOnCryptographicProviders() {
        assertViolation(
                ArchitectureRules.domainIsFrameworkIndependent(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.domain"),
                "DomainDependingOnCryptography",
                "java.security.Provider",
                "javax.crypto.Cipher");
    }

    @Test
    void rejectsApplicationDependingOnJackson() {
        assertViolation(
                ArchitectureRules.applicationIsIsolated(FIXTURE_ROOT + ".isolation"),
                importFixtures(FIXTURE_ROOT + ".isolation.application"),
                "ApplicationDependingOnJackson");
    }

    @Test
    void rejectsPackageCycle() {
        assertViolation(
                ArchitectureRules.packagesAreFreeOfCycles(FIXTURE_ROOT + ".cycle"),
                importFixtures(FIXTURE_ROOT + ".cycle"),
                "LeftCycle");
    }

    private static JavaClasses importFixtures(String packageName) {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.OnlyIncludeTests())
                .importPackages(packageName);
    }

    private static void assertViolation(ArchRule rule, JavaClasses classes, String... expectedFragments) {
        EvaluationResult result = rule.evaluate(classes);
        String report = result.getFailureReport().toString();
        assertTrue(result.hasViolation(), report);
        for (String expectedFragment : expectedFragments) {
            assertTrue(report.contains(expectedFragment), report);
        }
    }
}
