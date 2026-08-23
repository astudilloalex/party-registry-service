plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val archUnitVersion = "1.5.0"
val jsonSchemaValidatorVersion = "1.5.9"
val swaggerParserVersion = "2.1.47"
val wireMockVersion = "3.13.2"

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-arc")

    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-hibernate-reactive")
    implementation("io.quarkus:quarkus-reactive-pg-client")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-smallrye-context-propagation")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-opentelemetry")

    // JDBC is present only for Flyway's operational migration boundary.
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-test-vertx")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("com.tngtech.archunit:archunit-junit5:${archUnitVersion}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.wiremock:wiremock:${wireMockVersion}")
    testImplementation("io.swagger.parser.v3:swagger-parser:${swaggerParserVersion}")
    testImplementation("com.networknt:json-schema-validator:${jsonSchemaValidatorVersion}")
}

group = "com.alexastudillo"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

sourceSets {
    test {
        java.setSrcDirs(listOf("src/test/java"))
        resources.setSrcDirs(listOf("src/test/resources"))
    }
    named("integrationTest") {
        java.setSrcDirs(listOf("src/integrationTest/java"))
        resources.setSrcDirs(listOf("src/integrationTest/resources"))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Test>("quarkusIntTest") {
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(tasks.named("quarkusIntTest"))
}
