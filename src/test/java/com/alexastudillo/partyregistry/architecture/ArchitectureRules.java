package com.alexastudillo.partyregistry.architecture;

import com.alexastudillo.api.response.contract.ApiResponse;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
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

    /** Checks complete generic signatures, including collection payloads, instead of trusting erasure. */
    static ArchRule businessJsonResourcesUseSharedEnvelope(String rootPackage) {
        return methods().that().areDeclaredInClassesThat().resideInAPackage(rootPackage + ".api.resource..")
                .should(new BusinessJsonResponseCondition(rootPackage + ".api.model.response."));
    }

    /** Enforces the outbound JSON boundary while excluding explicitly protocol-native responses. */
    private static final class BusinessJsonResponseCondition extends ArchCondition<JavaMethod> {

        private final String responsePackage;

        private BusinessJsonResponseCondition(String responsePackage) {
            super("return Uni<RestResponse<ApiResponse<API response DTO>>> for business JSON endpoints");
            this.responsePackage = responsePackage;
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            Method reflected = method.reflect();
            if (!businessJsonEndpoint(reflected)) {
                return;
            }
            Optional<Type> payload = argument(reflected.getGenericReturnType(), Uni.class)
                    .flatMap(type -> argument(type, RestResponse.class))
                    .flatMap(type -> argument(type, ApiResponse.class));
            if (payload.filter(this::apiPayload).isEmpty()) {
                events.add(SimpleConditionEvent.violated(method,
                        method.getFullName() + " exposes an invalid business JSON response: "
                                + reflected.getGenericReturnType().getTypeName()));
            }
        }

        private boolean apiPayload(Type payload) {
            return responseDto(payload) || argument(payload, List.class).filter(this::responseDto).isPresent();
        }

        private boolean responseDto(Type payload) {
            return payload instanceof Class<?> dto && !dto.isInterface()
                    && !Modifier.isAbstract(dto.getModifiers())
                    && (dto.getPackageName() + ".").startsWith(responsePackage);
        }

        private static Optional<Type> argument(Type type, Class<?> wrapper) {
            if (type instanceof ParameterizedType parameterized && parameterized.getRawType().equals(wrapper)
                    && parameterized.getActualTypeArguments().length == 1) {
                return Optional.of(parameterized.getActualTypeArguments()[0]);
            }
            return Optional.empty();
        }

        private static boolean businessJsonEndpoint(Method method) {
            boolean endpoint = Arrays.stream(method.getAnnotations())
                    .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(HttpMethod.class));
            if (!endpoint || !Modifier.isPublic(method.getModifiers())) {
                return false;
            }
            Produces produces = method.getAnnotation(Produces.class);
            if (produces == null) {
                produces = method.getDeclaringClass().getAnnotation(Produces.class);
            }
            return produces == null || Arrays.stream(produces.value())
                    .map(MediaType::valueOf).anyMatch(type -> type.isCompatible(MediaType.APPLICATION_JSON_TYPE)
                            || type.getSubtype().endsWith("+json"));
        }
    }
}
