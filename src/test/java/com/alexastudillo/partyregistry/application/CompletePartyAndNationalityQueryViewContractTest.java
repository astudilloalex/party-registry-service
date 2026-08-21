package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.port;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.type;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.useCase;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.PartyStatus;
import com.alexastudillo.partyregistry.domain.PartyType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CompletePartyAndNationalityQueryViewContractTest {
    private static final UUID TENANT = UUID.fromString("078f0c72-4a7b-7c91-8b2a-2234567890a1");
    private static final UUID PARTY = UUID.fromString("078f0c72-4a7b-7c91-8b2a-2234567890a2");
    private static final UUID NATIONALITY = UUID.fromString("078f0c72-4a7b-7c91-8b2a-2234567890a3");
    private static final UUID PROCESS = UUID.fromString("078f0c72-4a7b-7c91-8b2a-2234567890a4");
    private static final Instant CREATED_AT = Instant.parse("2026-08-18T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-19T09:30:00Z");
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 20);

    @Test
    void partyQueryViewCarriesTenantIdentityTypeStatusVersionAndAllRequiredAuditFacts() {
        Object view = record("PartyView", facts(
                "id", PARTY,
                "tenantId", TENANT,
                "type", PartyType.NATURAL_PERSON,
                "displayName", "Synthetic Person",
                "status", PartyStatus.ACTIVE,
                "version", 14L));
        var query = port("PartyQueryPort").answers("findByTenantAndId", arguments -> {
            assertEquals(TENANT, arguments[0]);
            assertEquals(PARTY, arguments[1]);
            return CompletableFuture.completedFuture(view);
        });

        Object result = invoke(useCase("GetPartyUseCase", query.proxy()), "execute", context(), PARTY);

        assertAll("complete tenant-qualified Party output",
                () -> assertEquals(PARTY, fact(result, "id")),
                () -> assertEquals(TENANT, fact(result, "tenantId")),
                () -> assertEquals(PartyType.NATURAL_PERSON, fact(result, "type")),
                () -> assertEquals("Synthetic Person", fact(result, "displayName")),
                () -> assertEquals(PartyStatus.ACTIVE, fact(result, "status")),
                () -> assertEquals(14L, fact(result, "version")),
                () -> assertAudit(result));
        assertEquals(1, query.count("findByTenantAndId"));
        assertEquals(0, query.count("findById"));
    }

    @Test
    void naturalPersonDetailsQueryViewCarriesSourceFactsAndAllRequiredAuditFacts() {
        Object view = detailView(
                DetailKind.NATURAL_PERSON,
                "Synthetic",
                "Person",
                "Preferred Synthetic",
                "EC",
                LocalDate.of(1990, 2, 3),
                null);

        Object result = getDetailsThroughTenantQualifiedPort(view);

        assertAll("complete natural-person detail output",
                () -> assertEquals(PARTY, fact(result, "partyId")),
                () -> assertEquals(DetailKind.NATURAL_PERSON, fact(result, "kind")),
                () -> assertEquals("Synthetic", fact(result, "primaryName")),
                () -> assertEquals("Person", fact(result, "secondaryName")),
                () -> assertEquals("Preferred Synthetic", fact(result, "optionalName")),
                () -> assertEquals("EC", fact(result, "countryCode")),
                () -> assertEquals(LocalDate.of(1990, 2, 3), fact(result, "startDate")),
                () -> assertNull(fact(result, "endDate")),
                () -> assertAudit(result));
    }

    @Test
    void legalEntityDetailsQueryViewCarriesSourceFactsAndAllRequiredAuditFacts() {
        Object view = detailView(
                DetailKind.LEGAL_ENTITY,
                "Synthetic Entity S.A.",
                "EC_SA",
                "Synthetic Trade Name",
                "EC",
                LocalDate.of(2018, 4, 5),
                null);

        Object result = getDetailsThroughTenantQualifiedPort(view);

        assertAll("complete legal-entity detail output",
                () -> assertEquals(PARTY, fact(result, "partyId")),
                () -> assertEquals(DetailKind.LEGAL_ENTITY, fact(result, "kind")),
                () -> assertEquals("Synthetic Entity S.A.", fact(result, "primaryName")),
                () -> assertEquals("EC_SA", fact(result, "secondaryName")),
                () -> assertEquals("Synthetic Trade Name", fact(result, "optionalName")),
                () -> assertEquals("EC", fact(result, "countryCode")),
                () -> assertEquals(LocalDate.of(2018, 4, 5), fact(result, "startDate")),
                () -> assertNull(fact(result, "endDate")),
                () -> assertAudit(result));
    }

    @Test
    void nationalityWithNullValidityDatesCarriesExplicitActiveResultAndAllRequiredFacts() {
        LocalDate validFrom = null;
        LocalDate validUntil = null;
        Object view = nationalityView(true, validFrom, validUntil, approvedActiveAt(validUntil, AS_OF_DATE));

        Object result = getNationalityThroughTenantQualifiedPort(view);

        assertAll("complete active nationality output",
                () -> assertEquals(NATIONALITY, fact(result, "id")),
                () -> assertEquals(PARTY, fact(result, "partyId")),
                () -> assertEquals("EC", fact(result, "countryCode")),
                () -> assertEquals(true, fact(result, "primary")),
                () -> assertEquals(true, fact(result, "active")),
                () -> assertNull(fact(result, "validFrom")),
                () -> assertNull(fact(result, "validUntil")),
                () -> assertAudit(result));
    }

    @Test
    void nationalityEndedOnSuppliedDateCarriesExplicitInactiveResultAndPreservesValidity() {
        LocalDate validFrom = LocalDate.of(2020, 1, 2);
        LocalDate validUntil = AS_OF_DATE;
        Object view = nationalityView(false, validFrom, validUntil, approvedActiveAt(validUntil, AS_OF_DATE));

        Object result = getNationalityThroughTenantQualifiedPort(view);

        assertAll("complete ended nationality output",
                () -> assertEquals(NATIONALITY, fact(result, "id")),
                () -> assertEquals(PARTY, fact(result, "partyId")),
                () -> assertEquals("EC", fact(result, "countryCode")),
                () -> assertEquals(false, fact(result, "primary")),
                () -> assertEquals(false, fact(result, "active")),
                () -> assertEquals(validFrom, fact(result, "validFrom")),
                () -> assertEquals(validUntil, fact(result, "validUntil")),
                () -> assertAudit(result));
    }

    @Test
    void auditValueAndEveryOwningViewRejectMissingRequiredAuditFacts() {
        Class<?> auditType = type(APPLICATION + "AuditFacts");
        assertTrue(auditType.isRecord(), "Audit facts must be an immutable boundary-neutral value");

        for (String missing : new String[] {"createdAt", "createdBy", "updatedAt", "updatedBy"}) {
            Map<String, Object> incomplete = auditFacts();
            incomplete.put(missing, null);
            assertThrows(NullPointerException.class, () -> instantiate(auditType, incomplete),
                    "AuditFacts must reject null " + missing);
        }

        assertAll("every output view requires its audit value",
                () -> assertRejectsNullAudit("PartyView", facts(
                        "id", PARTY, "tenantId", TENANT, "type", PartyType.NATURAL_PERSON,
                        "displayName", "Synthetic Person", "status", PartyStatus.ACTIVE, "version", 14L)),
                () -> assertRejectsNullAudit("PartyDetailsView", detailFacts(
                        DetailKind.NATURAL_PERSON, "Synthetic", "Person", null, null, null, null)),
                () -> assertRejectsNullAudit("PartyDetailsView", detailFacts(
                        DetailKind.LEGAL_ENTITY, "Synthetic Entity", null, null, "EC", null, null)),
                () -> assertRejectsNullAudit("NationalityView", nationalityFacts(
                        false, null, AS_OF_DATE, false)));

        Class<?> activeType = Arrays.stream(type(APPLICATION + "NationalityView").getRecordComponents())
                .filter(component -> component.getName().equals("active"))
                .map(RecordComponent::getType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("NationalityView must carry the approved active output fact"));
        assertEquals(boolean.class, activeType, "OpenAPI-required active must be non-nullable");
    }

    private static Object getDetailsThroughTenantQualifiedPort(Object view) {
        var query = port("PartyQueryPort").answers("findDetails", arguments -> {
            assertEquals(TENANT, arguments[0]);
            assertEquals(PARTY, arguments[1]);
            return CompletableFuture.completedFuture(view);
        });
        Object result = invoke(useCase("GetPartyDetailsUseCase", query.proxy()), "execute", context(), PARTY);
        assertEquals(1, query.count("findDetails"));
        return result;
    }

    private static Object getNationalityThroughTenantQualifiedPort(Object view) {
        var query = port("PartyQueryPort").answers("findNationality", arguments -> {
            assertEquals(TENANT, arguments[0]);
            assertEquals(PARTY, arguments[1]);
            assertEquals(NATIONALITY, arguments[2]);
            return CompletableFuture.completedFuture(view);
        });
        Object result = invoke(
                useCase("GetNationalityUseCase", query.proxy()), "execute", context(), PARTY, NATIONALITY);
        assertEquals(1, query.count("findNationality"));
        return result;
    }

    private static Object detailView(
            DetailKind kind,
            String primaryName,
            String secondaryName,
            String optionalName,
            String countryCode,
            LocalDate startDate,
            LocalDate endDate) {
        return record("PartyDetailsView", detailFacts(
                kind, primaryName, secondaryName, optionalName, countryCode, startDate, endDate));
    }

    private static Map<String, Object> detailFacts(
            DetailKind kind,
            String primaryName,
            String secondaryName,
            String optionalName,
            String countryCode,
            LocalDate startDate,
            LocalDate endDate) {
        return facts(
                "partyId", PARTY,
                "kind", kind,
                "primaryName", primaryName,
                "secondaryName", secondaryName,
                "optionalName", optionalName,
                "countryCode", countryCode,
                "startDate", startDate,
                "endDate", endDate);
    }

    private static Object nationalityView(
            boolean primary, LocalDate validFrom, LocalDate validUntil, boolean active) {
        return record("NationalityView", nationalityFacts(primary, validFrom, validUntil, active));
    }

    private static Map<String, Object> nationalityFacts(
            boolean primary, LocalDate validFrom, LocalDate validUntil, boolean active) {
        return facts(
                "id", NATIONALITY,
                "partyId", PARTY,
                "countryCode", "EC",
                "primary", primary,
                "active", active,
                "validFrom", validFrom,
                "validUntil", validUntil);
    }

    private static boolean approvedActiveAt(LocalDate validUntil, LocalDate suppliedDate) {
        assertNotNull(suppliedDate, "the active mapping boundary must receive an explicit date");
        if (validUntil != null && validUntil.isAfter(suppliedDate)) {
            throw new AssertionError("Synthetic nationality end date violates the approved non-future boundary");
        }
        return validUntil == null;
    }

    private static void assertAudit(Object owner) {
        Object audit = fact(owner, "audit");
        assertAll("required audit facts",
                () -> assertEquals(CREATED_AT, fact(audit, "createdAt")),
                () -> assertEquals("synthetic-creator", fact(audit, "createdBy")),
                () -> assertEquals(UPDATED_AT, fact(audit, "updatedAt")),
                () -> assertEquals("synthetic-updater", fact(audit, "updatedBy")));
    }

    private static void assertRejectsNullAudit(String simpleName, Map<String, Object> ownerFacts) {
        ownerFacts.put("audit", null);
        assertThrows(NullPointerException.class, () -> instantiate(type(APPLICATION + simpleName), ownerFacts),
                simpleName + " must reject a null audit value");
    }

    private static Object record(String simpleName, Map<String, Object> ownerFacts) {
        return instantiate(type(APPLICATION + simpleName), ownerFacts);
    }

    private static Object instantiate(Class<?> recordType, Map<String, Object> suppliedFacts) {
        assertTrue(recordType.isRecord(), recordType.getName() + " must be a boundary-neutral record");
        RecordComponent[] components = recordType.getRecordComponents();
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            if (suppliedFacts.containsKey(component.getName())) {
                arguments[index] = suppliedFacts.get(component.getName());
            } else if (component.getName().equals("audit")) {
                arguments[index] = instantiate(component.getType(), auditFacts());
            } else {
                throw new AssertionError("No approved synthetic fact supplied for "
                        + recordType.getSimpleName() + "." + component.getName());
            }
        }
        try {
            Constructor<?> constructor = recordType.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("Boundary-neutral record constructor failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot construct boundary-neutral record " + recordType.getName(), exception);
        }
    }

    private static Map<String, Object> auditFacts() {
        return facts(
                "createdAt", CREATED_AT,
                "createdBy", "synthetic-creator",
                "updatedAt", UPDATED_AT,
                "updatedBy", "synthetic-updater");
    }

    private static Map<String, Object> facts(Object... namesAndValues) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            facts.put((String) namesAndValues[index], namesAndValues[index + 1]);
        }
        return facts;
    }

    private static Object fact(Object target, String name) {
        Method accessor = Arrays.stream(target.getClass().getMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        target.getClass().getSimpleName() + " does not preserve required fact " + name));
        return invoke(target, accessor.getName());
    }

    private static Object context() {
        return ApplicationContractSupport.construct(
                APPLICATION + "RequestContext",
                TENANT.toString(),
                "synthetic-query-actor",
                PROCESS.toString(),
                (Supplier<UUID>) () -> PROCESS);
    }
}
