package com.alexastudillo.partyregistry.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = CleanArchitectureTest.ROOT_PACKAGE,
        importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

    static final String ROOT_PACKAGE = "com.alexastudillo.partyregistry";

    private static final String DOMAIN = ROOT_PACKAGE + ".domain..";
    private static final String APPLICATION = ROOT_PACKAGE + ".application..";
    private static final String API = ROOT_PACKAGE + ".api..";
    private static final String INFRASTRUCTURE = ROOT_PACKAGE + ".infrastructure..";
    private static final String BOOTSTRAP = ROOT_PACKAGE + ".bootstrap..";
    private static final String PRODUCTION_CODE = ROOT_PACKAGE + "..";

    private static final Set<String> NATIVE_SQL_METHODS = Set.of(
            "createNativeQuery",
            "createNativeMutationQuery",
            "createNativeSelectionQuery",
            "createSQLQuery",
            "createSqlQuery");

    private static final DescribedPredicate<JavaClass> BLOCKING_TYPES = DescribedPredicate.describe(
            "a blocking I/O or worker-offloading type",
            CleanArchitectureTest::isBlockingType);

    private static final DescribedPredicate<JavaMethodCall> BLOCKING_CALLS = DescribedPredicate.describe(
            "a blocking wait or I/O method",
            CleanArchitectureTest::isBlockingCall);

    private static final DescribedPredicate<JavaMethodCall> WORKER_OFFLOADING_CALLS = DescribedPredicate.describe(
            "a worker-offloading method",
            CleanArchitectureTest::isWorkerOffloadingCall);

    private static final DescribedPredicate<JavaClass> DIRECT_SQL_TYPES = DescribedPredicate.describe(
            "a direct or native SQL API",
            CleanArchitectureTest::isDirectSqlType);

    private static final DescribedPredicate<JavaMethodCall> NATIVE_SQL_CALLS = DescribedPredicate.describe(
            "a Hibernate or JPA native SQL method",
            CleanArchitectureTest::isNativeSqlCall);

    private static final DescribedPredicate<JavaClass> POSTGRES_REACTIVE_CLIENT_TYPES = DescribedPredicate.describe(
            "a direct PostgreSQL or Vert.x SQL client type",
            CleanArchitectureTest::isPostgresReactiveClientType);

    private static final DescribedPredicate<JavaClass> BLOCKING_HIBERNATE_TYPES = DescribedPredicate.describe(
            "a blocking Hibernate ORM or JPA entry point",
            CleanArchitectureTest::isBlockingHibernateType);

    private static final DescribedPredicate<JavaClass> JDBC_TYPES = DescribedPredicate.describe(
            "a JDBC type",
            CleanArchitectureTest::isJdbcType);

    @ArchTest
    static final ArchRule DOMAIN_DEPENDENCIES_POINT_INWARD = dependenciesMustNotPointTo(
            DOMAIN,
            APPLICATION,
            API,
            INFRASTRUCTURE,
            BOOTSTRAP);

    @ArchTest
    static final ArchRule APPLICATION_DEPENDENCIES_POINT_INWARD = dependenciesMustNotPointTo(
            APPLICATION,
            API,
            INFRASTRUCTURE,
            BOOTSTRAP);

    @ArchTest
    static final ArchRule API_DEPENDENCIES_POINT_INWARD = dependenciesMustNotPointTo(
            API,
            INFRASTRUCTURE,
            BOOTSTRAP);

    @ArchTest
    static final ArchRule INFRASTRUCTURE_DEPENDENCIES_POINT_INWARD = dependenciesMustNotPointTo(
            INFRASTRUCTURE,
            API,
            BOOTSTRAP);

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
            .because("the domain must compile using only the JDK and other domain types")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_USE_BLOCKING_TYPES = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().dependOnClassesThat(BLOCKING_TYPES)
            .because("runtime I/O must remain non-blocking and must not be hidden by worker offloading")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_CALL_BLOCKING_METHODS = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().callMethodWhere(BLOCKING_CALLS)
            .because("reactive flows must never wait synchronously or invoke blocking I/O")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_OFFLOAD_BLOCKING_WORK = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().callMethodWhere(WORKER_OFFLOADING_CALLS)
            .because("worker-thread offloading must not conceal blocking work")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_USE_DIRECT_SQL_APIS = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().dependOnClassesThat(DIRECT_SQL_TYPES)
            .because("application persistence must use Hibernate Reactive ORM rather than direct SQL")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_CALL_NATIVE_SQL = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().callMethodWhere(NATIVE_SQL_CALLS)
            .because("native SQL is reserved for versioned Flyway migration resources")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_USE_POSTGRES_REACTIVE_CLIENT_DIRECTLY = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().dependOnClassesThat(POSTGRES_REACTIVE_CLIENT_TYPES)
            .because("the PostgreSQL reactive driver is an implementation detail of Hibernate Reactive")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_DOES_NOT_USE_BLOCKING_HIBERNATE = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().dependOnClassesThat(BLOCKING_HIBERNATE_TYPES)
            .because("persistence must use the Hibernate Reactive Mutiny API")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRODUCTION_CODE_OUTSIDE_FLYWAY_DOES_NOT_USE_JDBC = noClasses()
            .that().resideInAPackage(PRODUCTION_CODE)
            .should().dependOnClassesThat(JDBC_TYPES)
            .because("JDBC belongs only to Flyway's external migration runtime, not production application classes")
            .allowEmptyShould(true);

    private static ArchRule dependenciesMustNotPointTo(String sourcePackage, String... outerPackages) {
        return noClasses()
                .that().resideInAPackage(sourcePackage)
                .should().dependOnClassesThat().resideInAnyPackage(outerPackages)
                .because("Clean Architecture dependencies must point inward")
                .allowEmptyShould(true);
    }

    private static boolean isBlockingType(JavaClass type) {
        String name = type.getName();
        return name.startsWith("java.nio.file.")
                || name.equals("java.nio.channels.FileChannel")
                || name.equals("java.nio.channels.Channels")
                || isAssignableTo(type, "java.io.InputStream")
                || isAssignableTo(type, "java.io.OutputStream")
                || isAssignableTo(type, "java.io.Reader")
                || isAssignableTo(type, "java.io.Writer")
                || isAssignableTo(type, "java.net.Socket")
                || isAssignableTo(type, "java.net.ServerSocket")
                || isAssignableTo(type, "java.net.DatagramSocket")
                || name.equals("java.net.URLConnection")
                || name.equals("io.smallrye.common.annotation.Blocking")
                || name.equals("io.smallrye.common.annotation.RunOnVirtualThread")
                || name.equals("io.quarkus.vertx.web.Blocking");
    }

    private static boolean isBlockingCall(JavaMethodCall call) {
        String method = call.getName();
        return methodOn(call, "java.lang.Object", "wait")
                || methodOn(call, "java.lang.Thread", "sleep", "join")
                || methodOn(call, "java.lang.Process", "waitFor")
                || methodOn(call, "java.util.concurrent.Future", "get")
                || methodOn(call, "java.util.concurrent.CompletableFuture", "join")
                || methodOn(call, "java.util.concurrent.ForkJoinTask", "get", "join", "invoke")
                || methodOn(call, "java.util.concurrent.CountDownLatch", "await")
                || methodOn(call, "java.util.concurrent.CyclicBarrier", "await")
                || methodOn(call, "java.util.concurrent.Phaser", "awaitAdvance", "awaitAdvanceInterruptibly")
                || methodOn(call, "java.util.concurrent.Semaphore", "acquire", "acquireUninterruptibly")
                || methodOn(call, "java.util.concurrent.BlockingQueue", "put", "take")
                || methodOn(call, "java.util.concurrent.CompletionService", "take")
                || methodOn(call, "java.util.concurrent.ExecutorService", "invokeAll", "invokeAny", "awaitTermination")
                || methodOn(call, "java.util.concurrent.locks.Lock", "lock", "lockInterruptibly")
                || methodOn(call, "java.util.concurrent.locks.Condition", "await", "awaitNanos", "awaitUntil")
                || methodOn(call, "java.util.concurrent.locks.LockSupport", "park", "parkNanos", "parkUntil")
                || methodOn(call, "java.net.http.HttpClient", "send")
                || methodOn(call, "java.net.URL", "openConnection", "openStream", "getContent")
                || methodOn(call, "io.smallrye.mutiny.Uni", "await", "awaitUsing")
                || (call.getTargetOwner().getName().equals("io.smallrye.mutiny.groups.UniAwait")
                        && Set.of("indefinitely", "atMost", "asOptional").contains(method));
    }

    private static boolean isWorkerOffloadingCall(JavaMethodCall call) {
        String owner = call.getTargetOwner().getName();
        String method = call.getName();
        return (owner.equals("io.smallrye.mutiny.Uni") || owner.equals("io.smallrye.mutiny.Multi"))
                    && Set.of("emitOn", "runSubscriptionOn").contains(method)
                || owner.startsWith("io.vertx.") && method.equals("executeBlocking");
    }

    private static boolean isDirectSqlType(JavaClass type) {
        String name = type.getName();
        return name.startsWith("org.jooq.")
                || name.startsWith("org.jdbi.")
                || name.startsWith("org.springframework.jdbc.")
                || name.equals("jakarta.persistence.NamedNativeQuery")
                || name.equals("jakarta.persistence.NamedNativeQueries")
                || name.equals("jakarta.persistence.NamedStoredProcedureQuery")
                || name.equals("jakarta.persistence.NamedStoredProcedureQueries")
                || name.equals("jakarta.persistence.SqlResultSetMapping")
                || name.equals("jakarta.persistence.SqlResultSetMappings")
                || name.startsWith("org.hibernate.annotations.SQL")
                || name.equals("org.hibernate.annotations.Formula")
                || name.equals("org.hibernate.annotations.JoinFormula")
                || name.equals("org.hibernate.annotations.ColumnTransformer")
                || name.equals("org.hibernate.annotations.Subselect")
                || name.contains("org.hibernate.annotations.DialectOverride$Formula");
    }

    private static boolean isNativeSqlCall(JavaMethodCall call) {
        String owner = call.getTargetOwner().getName();
        return NATIVE_SQL_METHODS.contains(call.getName())
                && (owner.startsWith("org.hibernate.") || owner.startsWith("jakarta.persistence."));
    }

    private static boolean isPostgresReactiveClientType(JavaClass type) {
        String name = type.getName();
        return name.startsWith("io.vertx.pgclient.")
                || name.startsWith("io.vertx.mutiny.pgclient.")
                || name.startsWith("io.vertx.sqlclient.")
                || name.startsWith("io.vertx.mutiny.sqlclient.")
                || name.startsWith("io.reactiverse.pgclient.");
    }

    private static boolean isBlockingHibernateType(JavaClass type) {
        String name = type.getName();
        return name.equals("jakarta.persistence.EntityManager")
                || name.equals("jakarta.persistence.EntityManagerFactory")
                || name.equals("jakarta.persistence.Query")
                || name.equals("jakarta.persistence.TypedQuery")
                || name.equals("jakarta.persistence.StoredProcedureQuery")
                || name.startsWith("org.hibernate.Session")
                || name.startsWith("org.hibernate.StatelessSession")
                || name.equals("org.hibernate.SharedSessionContract")
                || name.startsWith("org.hibernate.query.")
                || name.startsWith("org.hibernate.jpa.")
                || name.startsWith("io.quarkus.hibernate.orm.");
    }

    private static boolean isJdbcType(JavaClass type) {
        String name = type.getName();
        return name.startsWith("java.sql.")
                || name.startsWith("javax.sql.")
                || name.startsWith("org.postgresql.")
                || name.startsWith("io.agroal.")
                || name.startsWith("io.quarkus.agroal.");
    }

    private static boolean methodOn(JavaMethodCall call, String ownerType, String... methodNames) {
        return isAssignableTo(call.getTargetOwner(), ownerType) && Set.of(methodNames).contains(call.getName());
    }

    private static boolean isAssignableTo(JavaClass type, String targetType) {
        return type.getName().equals(targetType) || type.isAssignableTo(targetType);
    }
}
