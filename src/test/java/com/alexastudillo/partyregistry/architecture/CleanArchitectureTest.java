package com.alexastudillo.partyregistry.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifies inward dependency direction and framework isolation across architectural boundaries.
 */
@AnalyzeClasses(
        packages = "com.alexastudillo.partyregistry",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule LAYERS_DEPEND_INWARD = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("API").definedBy("..api..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .whereLayer("API").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("API", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("API", "Application", "Infrastructure");

    @ArchTest
    static final ArchRule DOMAIN_AND_APPLICATION_HAVE_NO_OUTER_FRAMEWORK_DEPENDENCIES = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.quarkus..",
                    "org.hibernate..",
                    "jakarta.persistence..",
                    "jakarta.ws.rs..",
                    "..api..",
                    "..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule TOP_LEVEL_PACKAGES_ARE_FREE_OF_CYCLES = slices()
            .matching("com.alexastudillo.partyregistry.(*)..")
            .should().beFreeOfCycles();
}
