package com.alexastudillo.partyregistry.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Verifies production package boundaries and dependency direction.
 */
@AnalyzeClasses(packages = ArchitectureRules.PRODUCTION_ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

        @ArchTest
        static final ArchRule LAYER_DEPENDENCIES_POINT_INWARD = ArchitectureRules
                        .layerDependenciesPointInward(ArchitectureRules.PRODUCTION_ROOT);

        @ArchTest
        static final ArchRule DOMAIN_IS_FRAMEWORK_INDEPENDENT = ArchitectureRules
                        .domainIsFrameworkIndependent(ArchitectureRules.PRODUCTION_ROOT);

        @ArchTest
        static final ArchRule APPLICATION_IS_ISOLATED = ArchitectureRules
                        .applicationIsIsolated(ArchitectureRules.PRODUCTION_ROOT);

        @ArchTest
        static final ArchRule PACKAGES_ARE_FREE_OF_CYCLES = ArchitectureRules
                        .packagesAreFreeOfCycles(ArchitectureRules.PRODUCTION_ROOT);
}
