package com.alexastudillo.partyregistry.architecture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Defines reusable Clean Architecture constraints for production and fixtures.
 */
final class ArchitectureRules {

    static final String PRODUCTION_ROOT = "com.alexastudillo.partyregistry";
    private static final String[] FORBIDDEN_DOMAIN_PACKAGES = {
            "io.smallrye.mutiny..",
            "io.quarkus..",
            "org.hibernate..",
            "jakarta.persistence..",
            "javax.persistence..",
            "com.fasterxml.jackson..",
            "jakarta.ws.rs..",
            "org.jboss.resteasy..",
            "java.net.http..",
            "com.alexastudillo.api.response..",
            "java.security..",
            "javax.crypto.."
    };

    private ArchitectureRules() {
    }

    static ArchRule layerDependenciesPointInward(String rootPackage) {
        return layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage(rootPackage + "..")
                .withOptionalLayers(true)
                .layer("Domain").definedBy(rootPackage + ".domain..")
                .layer("Application").definedBy(rootPackage + ".application..")
                .layer("API").definedBy(rootPackage + ".api..")
                .layer("Infrastructure").definedBy(rootPackage + ".infrastructure..")
                .whereLayer("Domain").mayNotAccessAnyLayer()
                .whereLayer("Application").mayOnlyAccessLayers("Domain")
                .whereLayer("API").mayOnlyAccessLayers("Application", "Domain")
                .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain")
                .ensureAllClassesAreContainedInArchitecture();
    }

    static ArchRule domainIsFrameworkIndependent(String rootPackage) {
        return CompositeArchRule.of(classes()
                        .that().resideInAPackage(rootPackage + ".domain..")
                        .should().onlyDependOnClassesThat(resideInAnyPackage(
                                rootPackage + ".domain..",
                                "java..",
                                "org.jspecify.annotations..")))
                .and(noClasses()
                        .that().resideInAPackage(rootPackage + ".domain..")
                        .should().dependOnClassesThat(resideInAnyPackage(FORBIDDEN_DOMAIN_PACKAGES)));
    }

    static ArchRule applicationIsIsolated(String rootPackage) {
        return classes()
                .that().resideInAPackage(rootPackage + ".application..")
                .should().onlyDependOnClassesThat(resideInAnyPackage(
                        rootPackage + ".application..",
                        rootPackage + ".domain..",
                        "java..",
                        "io.smallrye.mutiny..",
                        "org.jspecify.annotations.."));
    }

    static ArchRule packagesAreFreeOfCycles(String rootPackage) {
        return slices()
                .matching(rootPackage + ".(**)")
                .should().beFreeOfCycles();
    }

    static ArchRule resourcesDelegateThroughUseCases(String rootPackage) {
        return noClasses().that().resideInAPackage(rootPackage + ".api.resource..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        rootPackage + ".application.port..", rootPackage + ".domain.policy..")
                .because("HTTP resources must delegate workflow and policy decisions to Application use cases");
    }
}
