# Plan 001: Configuración inicial del microservicio

## Objetivo

Establecer la base ejecutable de Party Registry antes de implementar endpoints de negocio.

## Pasos

1. Configurar Gradle, Quarkus y Java 25 para compilaciones JVM y nativa con REST, Mutiny, validación, OpenAPI, observabilidad, propagación de contexto, Hibernate Reactive with Panache, PostgreSQL y Flyway.
2. Crear los límites estrictos entre `api`, `application`, `domain` e `infrastructure`, y validar las dependencias hacia dentro con ArchUnit.
3. Configurar PostgreSQL reactivo para ejecución y JDBC exclusivamente para Flyway; generar la migración inicial versionada desde el DBML aprobado y deshabilitar la creación automática del esquema.
4. Implementar la base compartida de la API: sobre estándar, códigos estables, gestor global reactivo de errores, filtro de headers/MDC, formato exacto de logs y exposición de OpenAPI.
5. Definir los perfiles `dev`, `test` y `prod` mediante variables de entorno; añadir health, métricas, pruebas JVM, de integración y verificación de compilación nativa.

## Criterio de cierre

La aplicación inicia con la validación de Flyway, supera las pruebas de arquitectura y contrato, y genera artefactos JVM y nativo sin requerir endpoints de negocio.
