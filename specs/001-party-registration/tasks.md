---

description: "Tareas de implementación para el registro de Parties"
---

# Tareas: Registro de Parties

**Entrada**: Documentos de diseño en `specs/001-party-registration/`

**Prerrequisitos**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`, `traceability.md` y `contracts/`

**TDD obligatorio**: Cada comportamiento nuevo se implementa mediante un ciclo pequeño Red -> Green -> Refactor. La prueba Red debe compilar y fallar por el comportamiento ausente antes de iniciar Green.

**Organización**: Las tareas se agrupan por historia. Los fundamentos contienen únicamente capacidades compartidas que bloquean todas las historias.

## Formato

Cada tarea usa exactamente `- [ ] Tnnn [P?] [USn?] descripción con ruta`.

- **[P]**: Tarea paralelizable en archivos distintos y sin dependencia incompleta.
- **[US1]**, **[US2]**, **[US3]**: Etiqueta obligatoria dentro de fases de historias.
- Los identificadores y rutas de código permanecen en inglés; las instrucciones están en español.

## Precondiciones obligatorias

No se puede iniciar una tarea que produzca comportamiento, migraciones o adaptadores hasta aprobar y registrar:

- Validación independiente de `docs/database/v1-scheme.dbml`.
- Aprobación del alcance LikeC4 C2, ausencia intencional de consumidores aún inexistentes y
  propiedad del bounded context Party.
- ADR de Hibernate Reactive y su modelo de ejecución/transacción.
- Contrato Geographic Reference que exige exactamente un `Tenant-Id`, `User-Id` y `Process-Id`.

---

## Fase 1: Preparación

**Objetivo**: Preparar dependencias, source sets, fixtures y evidencia sin anticipar comportamiento.

- [x] T001 Documentar la aprobación de los cuatro gates en `specs/001-party-registration/implementation-readiness.md`
- [x] T002 Configurar en `build.gradle.kts` dependencias runtime y test para Quarkus REST/Jackson, REST Client/Jackson, Hibernate Reactive, PostgreSQL reactivo, Validator, OpenAPI, context propagation, Micrometer Prometheus, OpenTelemetry, Flyway/JDBC aislado, JUnit 5, Quarkus Vert.x test, RestAssured, ArchUnit, Testcontainers PostgreSQL, stub HTTP, parser OpenAPI 3.1 y validador JSON Schema 2020-12
- [ ] T003 Configurar en `build.gradle.kts` los source sets y tareas `test`, `check` y `quarkusIntTest` para `src/test/java` y `src/integrationTest/java`
- [ ] T004 [P] Crear fronteras de paquetes con `package-info.java` en `src/main/java/com/alexastudillo/partyregistry/domain/`, `application/`, `infrastructure/`, `api/` y `bootstrap/`
- [ ] T005 [P] Crear fixtures geográficos para estados, 404, 401/403, status/envelope/JSON/media type inesperados, respuesta incompleta/contradictoria, timeout, cancelación y eco incorrecto en `src/test/resources/geographic/`
- [ ] T006 Registrar `java -version` y `./gradlew --version` con Java 25 en `specs/001-party-registration/implementation-readiness.md`

**Checkpoint**: Dependencias resueltas, estructura compilable y gates externos aprobados.

---

## Fase 2: Fundamentos bloqueantes

**Objetivo**: Crear harnesses, scaffolding compilable y contexto compartido antes de las historias.

**CRÍTICO**: Esta fase bloquea US1, US2 y US3.

- [ ] T007 [P] Implementar reglas ArchUnit de capas hacia adentro, dominio sin frameworks y prohibición de blocking, SQL nativo, cliente PostgreSQL directo y JDBC fuera de Flyway en `src/test/java/com/alexastudillo/partyregistry/architecture/CleanArchitectureTest.java`
- [ ] T008 [P] Crear harness OpenAPI 3.1.1 y JSON Schema 2020-12 en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/ContractConformanceTest.java`
- [ ] T009 [P] Crear recurso Testcontainers PostgreSQL 18 en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/PostgreSqlTestResource.java` y `src/test/resources/application.properties`
- [ ] T010 [P] Crear stub HTTP controlado con conteo de requests, cancelación y delays en `src/test/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/GeographicReferenceStubResource.java`
- [ ] T011 Crear scaffolding compilable sin invariantes para `PartyType`, `PartyRecordStatus`, `CountryCode`, `Party`, `NaturalPersonDetails`, `LegalEntityDetails`, `NationalityPeriod` y `PartyNationality` en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/`
- [ ] T012 Crear scaffolding compilable sin comportamiento para comandos, contexto, intent de evento, resultados y outcomes en `src/main/java/com/alexastudillo/partyregistry/application/party/command/` y `result/`
- [ ] T013 Crear scaffolding compilable para `CreatePartyUseCase` en `src/main/java/com/alexastudillo/partyregistry/application/party/port/in/` y para puertos geográfico, persistence, política y reloj en `src/main/java/com/alexastudillo/partyregistry/application/party/port/out/`
- [ ] T014 Crear fakes reactivos con historial de llamadas y commit controlado en `src/test/java/com/alexastudillo/partyregistry/application/party/support/CreatePartyPortFakes.java`
- [ ] T015 Crear scaffolding REST compilable que responda sin comportamiento para DTOs, mapper, extractor, errores y recurso en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/`
- [ ] T016 Crear scaffolding compilable sin comportamiento para `CreatePartyService`, cliente/adaptador geográfico, `ConfiguredPartyEventPolicy`, entidades/mappers y adapter persistence en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java` y `src/main/java/com/alexastudillo/partyregistry/infrastructure/`

### Ciclo TDD compartido: Headers confiables

- [ ] T017 Red: Crear y ejecutar `./gradlew test --tests '*TrustedRequestContextTest'` para exigir exactamente un `Tenant-Id` UUID, `User-Id` no blank de hasta 128 y `Process-Id` UUID canónico preservado textualmente, con cero puertos ante ausencia, forma no canónica, malformación o duplicado, en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/TrustedRequestContextTest.java` (FR-002, FR-004, FR-005, FR-029)
- [ ] T018 Green: Implementar solo extracción y validación cardinal para pasar T017 en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/context/TrustedRequestContextExtractor.java` y `src/main/java/com/alexastudillo/partyregistry/application/party/command/TrustedCreationContext.java` (FR-002, FR-004, FR-005, FR-029)
- [ ] T019 Refactor: Mantener transporte fuera del dominio y ejecutar verdes T017 y `CleanArchitectureTest` sobre `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/context/`

### Ciclo TDD compartido: Body cerrado

- [ ] T020 Red: Crear y ejecutar `./gradlew test --tests '*ServerOwnedFieldsResourceTest'` para rechazar tenant, user, process, display name, status, versión, auditoría y campos desconocidos antes de puertos, con violations sanitizadas por JSON path, en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/ServerOwnedFieldsResourceTest.java` (FR-003, FR-011, FR-027)
- [ ] T021 Green: Implementar deserialización cerrada y mapper sin campos server-owned en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/dto/` y `mapper/PartyRequestMapper.java` (FR-003, FR-011, FR-027)
- [ ] T022 Refactor: Centralizar violations sanitizadas sin incluir valores rechazados y mantener verdes T020 y `ContractConformanceTest` en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/mapper/PartyRequestMapper.java`

### Ciclo TDD compartido: Esquema raíz

- [ ] T023 Red: Crear y ejecutar `./gradlew test --tests '*PartyRootMigrationTest'` para exigir enum, tabla `parties`, checks, índices e inmutabilidad de tipo; verificar fallo por migración ausente en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/PartyRootMigrationTest.java` (FR-006, FR-019, FR-020)
- [ ] T024 Green: Implementar solo el esquema requerido por T023 mediante Flyway en `src/main/resources/db/migration/V1__create_party_root.sql` (FR-006, FR-019, FR-020)
- [ ] T025 Refactor: Alinear nombres con `docs/database/v1-scheme.dbml` y mantener verde T023 sin modificar una migración ya aplicada en `src/main/resources/db/migration/V1__create_party_root.sql`

### Ciclo TDD compartido: ORM raíz

- [ ] T026 Red: Crear y ejecutar `./gradlew test --tests '*PartyRootEntityMappingTest'` para enum nombrado, UUID v7, versión `0`, schema generation deshabilitado, schema validation habilitado y JDBC aislado a Flyway; verificar fallo del scaffold/configuración en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/PartyRootEntityMappingTest.java` (FR-019, FR-020)
- [ ] T027 Green: Implementar el mapeo y configuración mínimos para pasar T026 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/entity/PartyEntity.java` y `src/main/resources/application.properties` (FR-019, FR-020)
- [ ] T028 Refactor: Encapsular mapping de raíz y mantener verdes T026 y `CleanArchitectureTest` en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/mapper/PartyRootPersistenceMapper.java`

- [ ] T029 Ejecutar `./gradlew test --tests '*CleanArchitectureTest' --tests '*ContractConformanceTest' --tests '*TrustedRequestContextTest' --tests '*PartyRoot*'` y registrar el checkpoint en `specs/001-party-registration/implementation-readiness.md`

**Checkpoint**: Los tests Red posteriores pueden compilar contra scaffolding y fallar por comportamiento ausente.

---

## Fase 3: Historia de Usuario 1 - Registrar persona natural (P1)

**Objetivo**: Crear una Party natural `DRAFT`, versión `0`, con países activos y hasta diez nacionalidades válidas.

**Prueba independiente**: Validar por aplicación, PostgreSQL y REST que se crea exactamente raíz más detalle natural y nacionalidades solicitadas, o cero filas para comandos inválidos.

### Ciclo US1.1: CountryCode

- [ ] T030 [P] [US1] Red: Implementar y ejecutar `./gradlew test --tests '*CountryCodeTest'` para mayúsculas y rechazo no alpha-2, verificando fallo de assertions contra el scaffold en `src/test/java/com/alexastudillo/partyregistry/domain/party/CountryCodeTest.java`
- [ ] T031 [US1] Green: Implementar solo normalización y validación para T030 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/CountryCode.java`
- [ ] T032 [US1] Refactor: Mantener inmutabilidad y verde T030 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/CountryCode.java`

### Ciclo US1.2: Composición natural

- [ ] T033 [US1] Red: Implementar y ejecutar `./gradlew test --tests '*NaturalPersonPartyTest'` para nombres, límites, detalle exclusivo, display derivado, `DRAFT` y versión `0` en `src/test/java/com/alexastudillo/partyregistry/domain/party/NaturalPersonPartyTest.java`
- [ ] T034 [US1] Green: Implementar creación natural mínima en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/NaturalPersonDetails.java` y `Party.java`
- [ ] T035 [US1] Refactor: Extraer reglas autoexplicativas y mantener verdes T033 y `CleanArchitectureTest` en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/Party.java`

### Ciclo US1.3: Fechas naturales

- [ ] T036 [US1] Red: Añadir y ejecutar casos abiertos, iguales, válidos y muerte anterior a nacimiento en `src/test/java/com/alexastudillo/partyregistry/domain/party/NaturalPersonPartyTest.java`
- [ ] T037 [US1] Green: Implementar validación mínima de vida en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/NaturalPersonDetails.java`
- [ ] T038 [US1] Refactor: Mantener la regla en dominio y verde T036 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/NaturalPersonDetails.java`

### Ciclo US1.4: Nacionalidad temporal

- [ ] T039 [P] [US1] Red: Implementar y ejecutar `./gradlew test --tests '*PartyNationalityTest'` para bounds abiertos/inclusivos, default false, actividad por fecha y fin anterior a inicio en `src/test/java/com/alexastudillo/partyregistry/domain/party/PartyNationalityTest.java`
- [ ] T040 [US1] Green: Implementar intervalo y nacionalidad mínimos en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/NationalityPeriod.java` y `PartyNationality.java`
- [ ] T041 [US1] Refactor: Centralizar evaluación temporal sin reloj global y mantener verde T039 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/NationalityPeriod.java`

### Ciclo US1.5: Colección de nacionalidades

- [ ] T042 [US1] Red: Añadir y ejecutar casos 0/10/11, país activo duplicado y dos primarias activas en `src/test/java/com/alexastudillo/partyregistry/domain/party/NaturalPersonPartyTest.java`
- [ ] T043 [US1] Green: Implementar límite, duplicidad activa y primaria única en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/Party.java`
- [ ] T044 [US1] Refactor: Proteger colecciones inmutables y mantener verdes T042 y T039 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/Party.java`

### Ciclo US1.6: Lookup geográfico activo y contexto

- [ ] T045 [US1] Red: Implementar y ejecutar `./gradlew test --tests '*GeographicReferenceAdapterTest.active*'` para ruta alpha-2, `ACTIVE`, deduplicación, conjunto vacío, exigencia y propagación exacta de `Tenant-Id`/`User-Id`/`Process-Id` como contrato completo de contexto, y eco de `Process-Id` en `src/test/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/GeographicReferenceAdapterTest.java`
- [ ] T046 [US1] Green: Implementar cliente, DTO y adapter mínimos para T045 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/client/GeographicReferenceClient.java`, `dto/CountryResponse.java`, `adapter/GeographicRequestContext.java` y `GeographicReferenceAdapter.java`
- [ ] T047 [US1] Refactor: Aislar DTOs/contexto del proveedor y mantener verdes T045 y `CleanArchitectureTest` en `src/main/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/`

### Ciclo US1.7: Fail-closed y resiliencia geográfica

- [ ] T048 [US1] Red: Añadir y ejecutar casos `DRAFT`/`DEPRECATED`/`RETIRED`, 404, 401/403, status/valor/envelope/JSON/media type inesperados, incompleto/contradictorio, timeout, conexión, cancelación, eco incorrecto y cero retries en `src/test/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/GeographicReferenceAdapterTest.java`
- [ ] T049 [US1] Green: Implementar outcomes, deadlines obligatorios, cancelación preservada y retries deshabilitados en `src/main/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/adapter/GeographicReferenceAdapter.java` y `src/main/resources/application.properties`
- [ ] T050 [US1] Refactor: Sanitizar internals sin añadir comportamiento y mantener verde toda la clase T048 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/integration/geographic/adapter/GeographicReferenceAdapter.java`

### Ciclo US1.8: Orquestación natural

- [ ] T051 [US1] Red: Implementar y ejecutar `./gradlew test --tests '*CreateNaturalPersonServiceTest'` para orden contexto -> dominio -> geografía -> política -> persistencia, conteo deduplicado, 11 nacionalidades antes de geografía, un único reloj para actor/timestamps/fecha, cero persistence ante rechazo y resultado solo tras commit en `src/test/java/com/alexastudillo/partyregistry/application/party/CreateNaturalPersonServiceTest.java` (FR-001, FR-014, FR-015, FR-020, FR-025, FR-026, FR-028)
- [ ] T052 [US1] Green: Implementar comando, reloj y orquestación natural mínimos para T051 en `src/main/java/com/alexastudillo/partyregistry/application/party/command/NaturalPersonInput.java`, `service/CreatePartyService.java` y `port/out/TimeProvider.java` (FR-001, FR-014, FR-015, FR-020, FR-025, FR-026, FR-028)
- [ ] T053 [US1] Refactor: Mantener explícito el orden probado y verdes T051/arquitectura en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java`

### Ciclo US1.9: Migración natural

- [ ] T054 [P] [US1] Red: Implementar y ejecutar `./gradlew test --tests '*NaturalPersonMigrationTest'` para tablas, checks, índices, `btree_gist`, rangos y conflictos concurrentes en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/NaturalPersonMigrationTest.java`
- [ ] T055 [US1] Green: Implementar solo el esquema requerido por T054 en `src/main/resources/db/migration/V2__create_natural_person_party.sql`
- [ ] T056 [US1] Refactor: Alinear constraints con DBML y mantener verde T054 sin cambiar migraciones aplicadas en `src/main/resources/db/migration/V2__create_natural_person_party.sql`

### Ciclo US1.10: Persistencia natural

- [ ] T057 [US1] Red: Implementar y ejecutar `./gradlew test --tests '*NaturalPersonPersistenceAdapterTest'` para raíz, detalle, nacionalidades, UUID v7, auditoría uniforme, versión `0` y respuesta solo después del commit en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/NaturalPersonPersistenceAdapterTest.java` (FR-019, FR-020, FR-021, FR-026)
- [ ] T058 [US1] Green: Implementar entidades, mappers y persistencia mínima para T057 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/entity/NaturalPersonDetailsEntity.java`, `PartyNationalityEntity.java`, `mapper/NaturalPersonPersistenceMapper.java` y `adapter/PartyPersistenceAdapter.java`
- [ ] T059 [US1] Refactor: Secuenciar sesión y evitar proxies fuera del adapter, manteniendo verdes T057 y arquitectura en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/`

### Ciclo US1.11: REST natural

- [ ] T060 [US1] Red: Implementar y ejecutar `./gradlew test --tests '*NaturalPersonResourceTest'` para tipo ausente/desconocido, variante cerrada, nulls/defaults, `201`, UUID v7, versión `0`, sin `Location`, y cero puertos para fechas/doble primaria inválidas en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/NaturalPersonResourceTest.java`
- [ ] T061 [US1] Green: Implementar DTO, mapping y recurso natural mínimos para T060 en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/dto/CreateNaturalPersonRequest.java`, `mapper/PartyRequestMapper.java` y `PartyResource.java`
- [ ] T062 [US1] Refactor: Mantener reglas fuera del recurso y verdes T060/contrato en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/`

### Ciclo US1.12: Vertical natural

- [ ] T063 [US1] Red: Implementar y ejecutar `./gradlew quarkusIntTest --tests '*NaturalPersonRegistrationIT'` para mínima, birth country, múltiples nacionalidades, primaria única y cero filas ante doble primaria/fechas inválidas en `src/integrationTest/java/com/alexastudillo/partyregistry/api/rest/v1/party/NaturalPersonRegistrationIT.java`
- [ ] T064 [US1] Green: Completar wiring CDI natural para T063 en `src/main/java/com/alexastudillo/partyregistry/bootstrap/PartyRegistryProducers.java`
- [ ] T065 [US1] Refactor: Ejecutar verde `./gradlew test --tests '*NaturalPerson*' quarkusIntTest --tests '*NaturalPersonRegistrationIT'` y ajustar solo composición en `src/main/java/com/alexastudillo/partyregistry/bootstrap/PartyRegistryProducers.java`

**Checkpoint US1**: Incremento natural demostrable; no liberable sin US3.

---

## Fase 4: Historia de Usuario 2 - Registrar entidad legal (P1)

**Objetivo**: Crear una Party legal `DRAFT`, versión `0`, con detalle legal exclusivo y país activo.

**Prueba independiente**: Validar raíz más detalle legal exactos, cero datos naturales/nacionalidades y cero filas ante combinaciones o fechas inválidas.

### Ciclo US2.1: Composición legal

- [ ] T066 [P] [US2] Red: Implementar y ejecutar `./gradlew test --tests '*LegalEntityPartyTest'` para nombre, límites, display, detalle exclusivo, ausencia de nacionalidades, `DRAFT` y versión `0` en `src/test/java/com/alexastudillo/partyregistry/domain/party/LegalEntityPartyTest.java`
- [ ] T067 [US2] Green: Implementar creación legal mínima para T066 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/LegalEntityDetails.java` y `Party.java`
- [ ] T068 [US2] Refactor: Mantener composición legal dentro del dominio y verdes T066/arquitectura en `src/main/java/com/alexastudillo/partyregistry/domain/party/`

### Ciclo US2.2: Fechas legales

- [ ] T069 [US2] Red: Añadir y ejecutar fechas abiertas, iguales, válidas y disolución anterior a incorporación en `src/test/java/com/alexastudillo/partyregistry/domain/party/LegalEntityPartyTest.java`
- [ ] T070 [US2] Green: Implementar validación mínima para T069 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/LegalEntityDetails.java`
- [ ] T071 [US2] Refactor: Mantener regla explícita y verde T069 en `src/main/java/com/alexastudillo/partyregistry/domain/party/model/LegalEntityDetails.java`

### Ciclo US2.3: Orquestación legal

- [ ] T072 [US2] Red: Implementar y ejecutar `./gradlew test --tests '*CreateLegalEntityServiceTest'` para incorporación activa, detalles cruzados/ambos, nacionalidades prohibidas, fechas inválidas y cero persistence para rechazo en `src/test/java/com/alexastudillo/partyregistry/application/party/CreateLegalEntityServiceTest.java`
- [ ] T073 [US2] Green: Implementar input y rama legal mínima en `src/main/java/com/alexastudillo/partyregistry/application/party/command/LegalEntityInput.java` y `service/CreatePartyService.java`
- [ ] T074 [US2] Refactor: Unificar flujo compartido sin mezclar políticas y mantener verdes T072/T051 en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java`

### Ciclo US2.4: Migración legal

- [ ] T075 [P] [US2] Red: Implementar y ejecutar `./gradlew test --tests '*LegalEntityMigrationTest'` para tabla, checks, FK y trigger diferido con competencia de detalles opuestos sin deadlock en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/LegalEntityMigrationTest.java`
- [ ] T076 [US2] Green: Implementar tabla legal y triggers aprobados en `src/main/resources/db/migration/V3__create_legal_entity_party.sql`
- [ ] T077 [US2] Refactor: Verificar locks/snapshot fresco y mantener verde T075 sin cambiar migración aplicada en `src/main/resources/db/migration/V3__create_legal_entity_party.sql`

### Ciclo US2.5: Persistencia legal

- [ ] T078 [US2] Red: Implementar y ejecutar `./gradlew test --tests '*LegalEntityPersistenceAdapterTest'` para raíz/detalle legal, auditoría, UUID v7 y cero filas naturales/nacionalidades en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/LegalEntityPersistenceAdapterTest.java`
- [ ] T079 [US2] Green: Implementar entidad, mapper y rama persistence legal en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/entity/LegalEntityDetailsEntity.java`, `mapper/LegalEntityPersistenceMapper.java` y `adapter/PartyPersistenceAdapter.java`
- [ ] T080 [US2] Refactor: Mantener mappers separados y ejecutar verdes T078/T057/arquitectura en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/`

### Ciclo US2.6: REST legal

- [ ] T081 [US2] Red: Implementar y ejecutar `./gradlew test --tests '*LegalEntityResourceTest'` para contrato válido, type ausente/desconocido, detalles cruzados/ambos, nacionalidades, fechas y cero puertos ante rechazo en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/LegalEntityResourceTest.java`
- [ ] T082 [US2] Green: Implementar DTO/mapping legal mínimos en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/dto/CreateLegalEntityRequest.java` y `mapper/PartyRequestMapper.java`
- [ ] T083 [US2] Refactor: Mantener unión discriminada cerrada y verdes T081/contrato en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/`

### Ciclo US2.7: Vertical legal

- [ ] T084 [US2] Red: Implementar y ejecutar `./gradlew quarkusIntTest --tests '*LegalEntityRegistrationIT'` para éxito, país inactivo y cero filas ante detalles incompatibles/fechas inválidas en `src/integrationTest/java/com/alexastudillo/partyregistry/api/rest/v1/party/LegalEntityRegistrationIT.java`
- [ ] T085 [US2] Green: Completar wiring legal para T084 en `src/main/java/com/alexastudillo/partyregistry/bootstrap/PartyRegistryProducers.java`
- [ ] T086 [US2] Refactor: Ejecutar verde `./gradlew test --tests '*NaturalPerson*' --tests '*LegalEntity*' quarkusIntTest` y ajustar solo composición en `src/main/java/com/alexastudillo/partyregistry/bootstrap/PartyRegistryProducers.java`

**Checkpoint US2**: Ambas variantes funcionan de manera independiente.

---

## Fase 5: Historia de Usuario 3 - Integridad de tenant y transacción (P1)

**Objetivo**: Aislar tenants, preservar auditoría/contexto y confirmar Party más evento opcional atómicamente.

**Prueba independiente**: Ejecutar ambos tipos con dos tenants, modos de evento y fallos inyectados; verificar ownership y cero estado parcial.

### Ciclo US3.1: Ownership y auditoría

- [ ] T087 [US3] Red: Implementar y ejecutar `./gradlew test --tests '*TenantAuditIsolationServiceTest'` con creaciones concurrentes para dos tenants/actores y reloj controlado, exigiendo cero contaminación cruzada y contexto request-local en `src/test/java/com/alexastudillo/partyregistry/application/party/TenantAuditIsolationServiceTest.java` (FR-001, FR-020)
- [ ] T088 [US3] Green: Implementar aislamiento request-local mínimo para T087 sin estado mutable compartido en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java` y `port/out/TimeProvider.java` (FR-001, FR-020)
- [ ] T089 [US3] Refactor: Mantener contexto explícito y verdes T087/T051/T072 en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java`

### Ciclo US3.2: Resultado geográfico y orden

- [ ] T090 [US3] Red: Implementar y ejecutar `./gradlew test --tests '*GeographicFailureServiceTest'` para categorías unknown/inactive/unavailable, resultado parcial, cero persistence/evento y orden completo contexto -> dominio -> geografía -> política -> persistence en `src/test/java/com/alexastudillo/partyregistry/application/party/GeographicFailureServiceTest.java`
- [ ] T091 [US3] Green: Implementar corte y mapping mínimo para T090 en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java`
- [ ] T092 [US3] Refactor: Expresar el pipeline sin introducir orden nuevo y mantener verde T090 en `src/main/java/com/alexastudillo/partyregistry/application/party/service/CreatePartyService.java`

### Ciclo US3.3: Política deshabilitada/excluida

- [ ] T093 [P] [US3] Red: Implementar y ejecutar `./gradlew test --tests '*PartyEventPolicyTest'` para modo disabled, allowlist excluida y modo habilitado con `party.created.v1` incluido, evaluados una vez por comando, en `src/test/java/com/alexastudillo/partyregistry/application/party/PartyEventPolicyTest.java` (FR-022, FR-023)
- [ ] T094 [US3] Green: Implementar las tres ramas de política mínimas para T093 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ConfiguredPartyEventPolicy.java` y `src/main/resources/application.properties` (FR-022, FR-023)
- [ ] T095 [US3] Refactor: Aislar configuración y mantener verdes T093/arquitectura en `src/main/java/com/alexastudillo/partyregistry/infrastructure/configuration/ConfiguredPartyEventPolicy.java`

### Ciclo US3.4: Intent de evento

- [ ] T096 [US3] Red: Implementar y ejecutar `./gradlew test --tests '*PartyCreationEventIntentTest'` para event type/schema, Party type, tenant, actor y occurrence time, sin aggregate ID, delivery status ni PII, en `src/test/java/com/alexastudillo/partyregistry/application/party/PartyCreationEventIntentTest.java` (FR-023, FR-024)
- [ ] T097 [US3] Green: Implementar intent tecnológico-neutral y creación condicional para T096 en `src/main/java/com/alexastudillo/partyregistry/application/party/command/PartyCreationEventIntent.java` y `service/CreatePartyService.java` (FR-023, FR-024)
- [ ] T098 [US3] Refactor: Mantener el intent inmutable y tecnológico-neutral, ejecutar verde T096 y no agregar payload serializado ni estado delivery en `src/main/java/com/alexastudillo/partyregistry/application/party/command/PartyCreationEventIntent.java`

### Ciclo US3.5: Migración outbox

- [ ] T099 [P] [US3] Red: Implementar y ejecutar `./gradlew test --tests '*PartyOutboxMigrationTest'` para enums, checks, unique identity, payload shape y auditoría en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/PartyOutboxMigrationTest.java`
- [ ] T100 [US3] Green: Implementar esquema outbox aprobado en `src/main/resources/db/migration/V4__create_party_outbox.sql`
- [ ] T101 [US3] Refactor: Alinear constraints con DBML y mantener verde T099 sin publicación ni tablas fuera de alcance en `src/main/resources/db/migration/V4__create_party_outbox.sql`

### Ciclo US3.6: Commit atómico y auditoría outbox

- [ ] T102 [US3] Red: Implementar y ejecutar `./gradlew test --tests '*AtomicPartyOutboxPersistenceTest.success*'` para Party completa más cero/un evento `PENDING`, aggregate/version/schema, payload validado por JSON Schema, attempts/version `0`, timestamps/actor, campos nullable y éxito post-commit en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/AtomicPartyOutboxPersistenceTest.java` (FR-020, FR-021, FR-022, FR-023, FR-024)
- [ ] T103 [US3] Green: Implementar entidad, mapping de payload y una única `withTransaction` para T102 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/entity/PartyOutboxEventEntity.java`, `mapper/PartyOutboxPersistenceMapper.java` y `adapter/PartyPersistenceAdapter.java` (FR-020, FR-021, FR-022, FR-023, FR-024)
- [ ] T104 [US3] Refactor: Secuenciar raíz, graph y outbox en una sesión y mantener verdes T102/T057/T078 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/adapter/PartyPersistenceAdapter.java`

### Ciclo US3.7: Rollback

- [ ] T105 [US3] Red: Añadir y ejecutar fallos en raíz, detalle, nacionalidad, outbox, flush y commit, exigiendo cero filas/evento/éxito en `src/test/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/AtomicPartyOutboxPersistenceTest.java`
- [ ] T106 [US3] Green: Implementar propagación y rollback reactivo mínimos para T105 en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/adapter/PartyPersistenceAdapter.java`
- [ ] T107 [US3] Refactor: Eliminar paralelismo de sesión/suscripción manual y mantener verde toda la suite persistence en `src/main/java/com/alexastudillo/partyregistry/infrastructure/persistence/party/adapter/PartyPersistenceAdapter.java`

### Ciclo US3.8: RFC 9457 y datos sensibles

- [ ] T108 [US3] Red: Implementar y ejecutar `./gradlew test --tests '*PartyProblemMapperTest'` para 400/415/422/503/500, códigos estables y ausencia de PII/provider/SQL/stack traces en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/PartyProblemMapperTest.java`
- [ ] T109 [US3] Green: Implementar Problem Details y mapper mínimos para T108 en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/error/PartyProblem.java` y `PartyExceptionMapper.java`
- [ ] T110 [US3] Refactor: Centralizar códigos sin filtrar internals y mantener verdes T108/contrato en `src/main/java/com/alexastudillo/partyregistry/api/rest/v1/party/error/`

### Ciclo US3.9: Observabilidad no bloqueante

- [ ] T111 [US3] Red: Implementar y ejecutar `./gradlew test --tests '*ReactiveContextObservabilityTest'` para patrón exacto, limpieza MDC, event-loop, spans/métricas automáticos de HTTP/REST Client/Hibernate y atributos trusted sin PII en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/ReactiveContextObservabilityTest.java`
- [ ] T112 [US3] Green: Habilitar Micrometer/OpenTelemetry y propagar/limpiar atributos trusted para T111 en `src/main/resources/application.properties` y `src/main/java/com/alexastudillo/partyregistry/infrastructure/observability/ReactiveRequestContext.java`
- [ ] T113 [US3] Refactor: Mantener instrumentación en adapters y dominio libre de observabilidad, ejecutando verdes T111/arquitectura sobre `src/main/java/com/alexastudillo/partyregistry/infrastructure/observability/`

### Ciclo US3.10: Matriz empaquetada

- [ ] T114 [US3] Red: Implementar y ejecutar `./gradlew quarkusIntTest --tests '*TenantTransactionIntegrityIT'` con dos tenants, tres headers, ambos tipos, eventos disabled/excluded/enabled, fallos geográficos/atómicos y event-loop en `src/integrationTest/java/com/alexastudillo/partyregistry/api/rest/v1/party/TenantTransactionIntegrityIT.java`
- [ ] T115 [US3] Green: Completar wiring/perfiles y respuesta post-commit para T114 en `src/main/java/com/alexastudillo/partyregistry/bootstrap/PartyRegistryProducers.java` y `src/main/resources/application.properties`
- [ ] T116 [US3] Refactor: Ejecutar verde `./gradlew test quarkusIntTest` y ajustar solo composición en `src/main/java/com/alexastudillo/partyregistry/bootstrap/PartyRegistryProducers.java`

**Checkpoint US3**: Contexto, tenant, auditoría, observabilidad y transacción cumplen fail-closed y atomicidad.

---

## Fase 6: Pulido y controles transversales

**Objetivo**: Cerrar evidencia y trazabilidad sin agregar comportamiento.

- [ ] T117 [P] Ejecutar validadores y comparar contrato runtime con `specs/001-party-registration/contracts/party-registration.openapi.yaml` y `party-created-v1.schema.json`
- [ ] T118 [P] Ejecutar `./gradlew test --tests '*CleanArchitectureTest'` y corregir únicamente violaciones en `src/main/java/com/alexastudillo/partyregistry/`
- [ ] T119 Ejecutar `./gradlew clean test` y registrar evidencia en `specs/001-party-registration/implementation-readiness.md`
- [ ] T120 Ejecutar el gate completo `./gradlew clean check quarkusIntTest` y registrar evidencia en `specs/001-party-registration/implementation-readiness.md`
- [ ] T121 Ejecutar escenarios no productivos de `specs/001-party-registration/quickstart.md` con PostgreSQL 18 y stub geográfico
- [ ] T122 Actualizar FR-001..FR-029, SC-001..SC-007 y cada escenario de aceptación con clases reales en `specs/001-party-registration/traceability.md`
- [ ] T123 Ejecutar y revisar assertions sensibles existentes en `src/test/java/com/alexastudillo/partyregistry/api/rest/v1/party/PartyProblemMapperTest.java`, `ReactiveContextObservabilityTest.java` y `src/test/java/com/alexastudillo/partyregistry/application/party/PartyCreationEventIntentTest.java`
- [ ] T124 Registrar comandos/exit codes Red-Green, gates y correspondencia DBML/Flyway/Hibernate en `specs/001-party-registration/implementation-readiness.md`

---

## Trazabilidad de ciclos

| Tareas | Requisitos y escenarios principales |
|--------|--------------------------------------|
| T017-T019 | FR-002, FR-004, FR-005, FR-029; US3 contexto inválido |
| T020-T022 | FR-003, FR-011, FR-027; US3 ownership no confiable |
| T023-T028 | FR-006, FR-019, FR-020 |
| T030-T035 | FR-006, FR-007, FR-008, FR-011, FR-019; US1 escenario 1 |
| T036-T044 | FR-012, FR-013, FR-016, FR-017, FR-018, FR-028; US1 escenarios 4-6 |
| T045-T050 | FR-014, FR-015, FR-029; US1 escenarios 2-3 y US3 escenarios 4-5 |
| T051-T053 | FR-001, FR-005, FR-014, FR-015, FR-020, FR-025, FR-026, FR-028 |
| T054-T059 | FR-016, FR-018, FR-019, FR-020, FR-021, FR-025 |
| T060-T065 | FR-001, FR-002, FR-004, FR-005, FR-007, FR-008, FR-026, FR-027; todos los escenarios US1 |
| T066-T074 | FR-006, FR-009, FR-010, FR-011, FR-012, FR-014, FR-015, FR-019, FR-025; US2 escenarios 1-3 |
| T075-T080 | FR-009, FR-019, FR-020, FR-021, FR-025 |
| T081-T086 | FR-001, FR-002, FR-004, FR-005, FR-009, FR-010, FR-026, FR-027; todos los escenarios US2 |
| T087-T092 | FR-001, FR-014, FR-015, FR-020, FR-025; US3 escenarios 3-5 |
| T093-T098 | FR-022, FR-023, FR-024; US3 escenarios 7-8 |
| T099-T107 | FR-020, FR-021, FR-022, FR-023, FR-024, FR-025; US3 escenarios 6-8 |
| T108-T116 | FR-001, FR-005, FR-015, FR-025, FR-026, FR-027, FR-029; US3 escenarios 1-9 |
| T117-T124 | SC-001..SC-007 y gates constitucionales |

---

## Dependencias y orden

### Grafo

```text
Preparación -> Fundamentos -> US1 -----\
                           \-> US2 -----> US3 -> Pulido
```

### Dependencias

- Fase 1 no tiene dependencia; T001 bloquea comportamiento hasta aprobar gates.
- Fase 2 depende de Fase 1 y bloquea todas las historias.
- US1 y US2 dependen de Fase 2; sus ciclos de dominio/migración pueden avanzar en paralelo.
- Las tareas de US1 y US2 que modifican `Party.java`, `CreatePartyService.java`, `PartyPersistenceAdapter.java`, `PartyRequestMapper.java` o `PartyRegistryProducers.java` deben serializarse.
- US3 depende de US1 y US2 completos; esta secuencia preserva Flyway V1 -> V2 -> V3 -> V4 y permite probar ambos tipos.
- Pulido depende de las tres historias.

### Regla TDD

- Red compila y falla por la assertion de comportamiento ausente.
- Green implementa solo lo exigido por su Red.
- Refactor no agrega comportamiento; comienza y termina verde.
- No se omite, deshabilita ni debilita una prueba.

---

## Ejemplos paralelos

### US1

```text
T030-T032 CountryCode
T039-T041 PartyNationality
T054-T056 migración natural
```

### US2

```text
T066-T068 dominio legal
T075-T077 migración legal
```

### US3

```text
T093-T095 política de evento
T099-T101 migración outbox
```

Los ciclos convergen antes de modificar archivos compartidos.

---

## Estrategia de implementación

### Primer incremento demostrable

1. Completar Preparación y Fundamentos.
2. Completar US1.
3. Validar `NaturalPersonRegistrationIT`.
4. No liberar: US3 contiene garantías obligatorias.

### Alcance MVP recomendado

US1 es el MVP funcional demostrable. Debido a que US2 también es P1 y US3 es una condición de seguridad no negociable, el primer release debe completar US1, US2 y US3 antes del Pulido final.

### Entrega incremental

1. Fundamentos compartidos.
2. US1 natural independiente.
3. US2 legal independiente.
4. US3 tenant/transacción sobre ambos tipos.
5. Gates y trazabilidad finales.

## Notas

- No se implementan publicación RabbitMQ, updates, búsquedas, identificadores oficiales ni retries de creación.
- No se usa H2, SQL directo/nativo en producción, cliente PostgreSQL directo, Hibernate bloqueante ni JDBC fuera de Flyway.
- Cada Red/Green conserva comando, exit code y motivo esperado en la Pull Request o `implementation-readiness.md`.
