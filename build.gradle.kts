plugins {
    java
    jacoco
    id("io.quarkus")
}

repositories {
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val swaggerParserVersion = "2.1.34"
val jsonSchemaValidatorVersion = "1.5.6"
val testcontainersVersion = "2.0.4"
val archUnitVersion = "1.5.0"
val jacocoVersion = "0.8.15"

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-reactive-pg-client")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-cyclonedx")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-rabbitmq:$testcontainersVersion")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archUnitVersion")
    testImplementation("io.swagger.parser.v3:swagger-parser-v3:$swaggerParserVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
}

group = "com.alexastudillo"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

jacoco {
    toolVersion = jacocoVersion
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

dependencyLocking {
    lockAllConfigurations()
    lockFile = file("gradle/dependency-locks/gradle.lockfile")
}

tasks.register<JavaExec>("validateContracts") {
    group = "verification"
    description = "Validates the configured OpenAPI 3.1 document, event schema, and every event example."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.alexastudillo.partyregistry.contractvalidation.ContractValidationHarness"
    args(
        providers.gradleProperty("contractOpenApi")
            .getOrElse("api/openapi/v1/party-registry.openapi.yaml"),
        providers.gradleProperty("contractEventSchema")
            .getOrElse("api/events/v1/party-registry-events.schema.json"),
        providers.gradleProperty("contractEventExamples")
            .getOrElse("api/events/v1/examples")
    )
}
