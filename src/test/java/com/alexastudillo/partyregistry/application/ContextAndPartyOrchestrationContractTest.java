package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.assertFailureCode;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.construct;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.sequencedPort;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.useCase;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ContextAndPartyOrchestrationContractTest {
    private static final UUID TENANT = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ac");
    private static final UUID OTHER_TENANT = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ad");
    private static final UUID PROCESS = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ae");
    private static final UUID PARTY = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ab");
    private static final UUID NATIONALITY = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890af");
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    void requestContextRejectsInvalidValuesAndDeterministicallyGeneratesMissingProcessId() {
        Supplier<UUID> processIds = () -> PROCESS;

        Object generated = construct(
                APPLICATION + "RequestContext", TENANT.toString(), "synthetic-service", null, processIds);
        assertEquals(TENANT, invoke(generated, "tenantId"));
        assertEquals("synthetic-service", invoke(generated, "userId"));
        assertEquals(PROCESS, invoke(generated, "processId"));

        assertFailureCode("VALIDATION_ERROR", () -> construct(
                APPLICATION + "RequestContext", TENANT.toString().toUpperCase(), "synthetic-service", null, processIds));
        assertFailureCode("VALIDATION_ERROR", () -> construct(
                APPLICATION + "RequestContext", TENANT.toString(), " ", PROCESS.toString(), processIds));
        assertFailureCode("VALIDATION_ERROR", () -> construct(
                APPLICATION + "RequestContext", TENANT.toString(), "x".repeat(129), PROCESS.toString(), processIds));
        assertFailureCode("VALIDATION_ERROR", () -> construct(
                APPLICATION + "RequestContext", TENANT.toString(), "synthetic-service", "not-a-uuid", processIds));
    }

    @Test
    void tenantScopedLookupPassesOnlyContextTenantAndMapsCrossTenantDataToNotFound() {
        var query = ApplicationContractSupport.port("PartyQueryPort")
                .answers("findByTenantAndId", arguments -> {
                    assertEquals(TENANT, arguments[0]);
                    assertEquals(PARTY, arguments[1]);
                    return CompletableFuture.completedFuture(null);
                });
        Object service = useCase("GetPartyUseCase", query.proxy());

        assertFailureCode("NOT_FOUND", () -> invoke(service, "execute", context(), PARTY));
        assertEquals(1, query.count("findByTenantAndId"));
        assertEquals(0, query.count("findById"));
    }

    @Test
    void missingIfMatchWinsBeforeGeographyOrBusinessDataAccess() {
        List<String> sequence = new ArrayList<>();
        var geography = sequencedPort("GeographicReferencePort", sequence)
                .returns("resolveActive", CompletableFuture.completedFuture(countryEvidence()));
        var unitOfWork = sequencedPort("PartyUnitOfWorkPort", sequence)
                .returns("updateNationalityAndAppendOutbox", CompletableFuture.completedFuture(mutationResult()));
        Object service = useCase("UpdateNationalityUseCase", geography.proxy(), unitOfWork.proxy());

        assertFailureCode("PRECONDITION_REQUIRED", () -> invoke(service, "execute",
                context(), PARTY, NATIONALITY, "EC", LocalDate.of(2020, 1, 1), null, null));

        assertEquals(List.of(), sequence);
    }

    @Test
    void countryEvidenceCompletesBeforeAtomicMutationAndOutboxIntent() {
        List<String> sequence = new ArrayList<>();
        var geography = sequencedPort("GeographicReferencePort", sequence)
                .returns("resolveActive", CompletableFuture.completedFuture(countryEvidence()));
        var unitOfWork = sequencedPort("PartyUnitOfWorkPort", sequence)
                .answers("updateNationalityAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertEquals(TENANT, invoke(intent, "tenantId"));
                    assertEquals(PARTY, invoke(intent, "partyId"));
                    assertEquals(7L, invoke(intent, "expectedVersion"));
                    assertEquals("party.nationality-updated.v1", invoke(invoke(intent, "outbox"), "eventType"));
                    assertEquals(PROCESS, invoke(invoke(intent, "outbox"), "correlationId"));
                    return CompletableFuture.completedFuture(mutationResult());
                });
        Object service = useCase("UpdateNationalityUseCase", geography.proxy(), unitOfWork.proxy());

        Object result = invoke(service, "execute",
                context(), PARTY, NATIONALITY, "EC", LocalDate.of(2020, 1, 1), null, 7L);

        assertEquals(List.of(
                "GeographicReferencePort.resolveActive",
                "PartyUnitOfWorkPort.updateNationalityAndAppendOutbox"), sequence);
        assertEquals(8L, invoke(result, "version"));
        assertNotNull(invoke(result, "outboxEventId"));
    }

    @Test
    void unavailableGeographyLeavesMutationAndOutboxUncalled() {
        List<String> sequence = new ArrayList<>();
        var geography = sequencedPort("GeographicReferencePort", sequence)
                .answers("resolveActive", ignored -> CompletableFuture.failedFuture(
                        (Throwable) construct(
                                APPLICATION + "ApplicationFailure", "DEPENDENCY_UNAVAILABLE", "country unavailable")));
        var unitOfWork = sequencedPort("PartyUnitOfWorkPort", sequence)
                .returns("updateNationalityAndAppendOutbox", CompletableFuture.completedFuture(mutationResult()));
        Object service = useCase("UpdateNationalityUseCase", geography.proxy(), unitOfWork.proxy());

        assertFailureCode("DEPENDENCY_UNAVAILABLE", () -> invoke(service, "execute",
                context(), PARTY, NATIONALITY, "EC", LocalDate.of(2020, 1, 1), null, 7L));

        assertEquals(List.of("GeographicReferencePort.resolveActive"), sequence);
        assertEquals(0, unitOfWork.count("updateNationalityAndAppendOutbox"));
    }

    @Test
    void versionConflictIsOneFailedAtomicMutationWithNoSeparateOutboxCall() {
        List<String> sequence = new ArrayList<>();
        var geography = sequencedPort("GeographicReferencePort", sequence)
                .returns("resolveActive", CompletableFuture.completedFuture(countryEvidence()));
        var unitOfWork = sequencedPort("PartyUnitOfWorkPort", sequence)
                .returns("updateNationalityAndAppendOutbox", CompletableFuture.failedFuture(
                        (Throwable) construct(
                                APPLICATION + "ApplicationFailure", "VERSION_CONFLICT", "stale aggregate version")));
        Object service = useCase("UpdateNationalityUseCase", geography.proxy(), unitOfWork.proxy());

        assertFailureCode("VERSION_CONFLICT", () -> invoke(service, "execute",
                context(), PARTY, NATIONALITY, "EC", LocalDate.of(2020, 1, 1), null, 6L));

        assertEquals(List.of(
                "GeographicReferencePort.resolveActive",
                "PartyUnitOfWorkPort.updateNationalityAndAppendOutbox"), sequence);
        assertEquals(1, unitOfWork.count("updateNationalityAndAppendOutbox"));
        assertEquals(0, unitOfWork.count("appendOutbox"));
    }

    private static Object context() {
        return construct(APPLICATION + "RequestContext", TENANT.toString(), "synthetic-service", PROCESS.toString(),
                (Supplier<UUID>) () -> OTHER_TENANT);
    }

    private static Object countryEvidence() {
        return value("CountryEvidence", "EC", Instant.parse("2026-08-15T12:00:00Z"));
    }

    private static Object mutationResult() {
        return value("MutationResult", PARTY, 8L,
                UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b0"), NOW);
    }
}
