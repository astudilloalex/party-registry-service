package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.configuration.PartyOutboxPersistenceMode;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies activation transaction decisions, safe writes, and failure classification without a database.
 */
class HibernateReactivePartyActivationAdapterTest {

    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991b4f-3400-7000-8000-000000000001"));
    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("01991b4f-3400-7000-8000-000000000002"));
    private static final UUID PROCESS_ID = UUID.fromString(
            "01991b4f-3400-7000-8000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-09-04T09:00:00Z");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-09-04T10:00:00Z");
    private static final String USER_ID = "activation-unit-test";

    private final PartyActivationDecision decision = new PartyActivationDecision();

    @Test
    void enforcesAbsenceStaleLifecycleAndEligibilityPrecedenceExactly() {
        assertFailure(
                ApplicationFailure.PartyNotFound.class,
                () -> decision.activate(candidate(PartyVersion.initial()), null, null));

        NaturalPerson activeAtTwo = party(PartyRecordStatus.ACTIVE, new PartyVersion(2));
        ApplicationFailure.StalePartyVersion stale = assertInstanceOf(
                ApplicationFailure.StalePartyVersion.class,
                assertFailure(
                        ApplicationFailure.StalePartyVersion.class,
                        () -> decision.activate(
                                candidate(PartyVersion.initial()),
                                activeAtTwo,
                                null)));
        assertEquals(PartyVersion.initial(), stale.expectedVersion());
        assertEquals(new PartyVersion(2), stale.currentVersion());

        ApplicationFailure.InvalidPartyLifecycle lifecycle = assertInstanceOf(
                ApplicationFailure.InvalidPartyLifecycle.class,
                assertFailure(
                        ApplicationFailure.InvalidPartyLifecycle.class,
                        () -> decision.activate(
                                candidate(new PartyVersion(2)),
                                activeAtTwo,
                                null)));
        assertEquals(PartyRecordStatus.ACTIVE, lifecycle.currentStatus());

        assertFailure(
                ApplicationFailure.MissingQualifyingIdentifier.class,
                () -> decision.activate(
                        candidate(new PartyVersion(2)),
                        party(PartyRecordStatus.DRAFT, new PartyVersion(2)),
                        List.of()));
    }

    @Test
    void mapsActivatedStatusAndAuditWhileLeavingJpaVersionForHibernate() {
        NaturalPerson draft = party(PartyRecordStatus.DRAFT, PartyVersion.initial());
        NaturalPerson activated = draft.activate(ACTIVATED_AT, USER_ID);
        PartyEntity entity = new NaturalPersonPersistenceMapper().toEntity(draft);

        entity.applyActivation(activated);

        assertEquals(PartyRecordStatus.ACTIVE, entity.recordStatus());
        assertEquals(ACTIVATED_AT, entity.updatedAt());
        assertEquals(USER_ID, entity.updatedBy());
        assertEquals(0L, entity.version());
        assertEquals(CREATED_AT, entity.createdAt());
        assertEquals("creator", entity.createdBy());
    }

    @Test
    void constructsOneMinimalSafeVersionedActivationEvent() {
        PartyActivationCandidate candidate = candidate(PartyVersion.initial());
        NaturalPerson activated = party(PartyRecordStatus.DRAFT, PartyVersion.initial())
                .activate(ACTIVATED_AT, USER_ID);

        PartyActivatedOutboxCandidate event =
                HibernateReactivePartyActivationAdapter.activationEvent(candidate, activated);
        PartyOutboxEventEntity entity = new PartyOutboxEventPersistenceMapper().toEntity(event);

        assertEquals(7, event.eventId().version());
        assertEquals(PartyActivatedOutboxCandidate.EVENT_TYPE, event.eventType());
        assertEquals((short) 1, event.eventSchemaVersion());
        assertEquals(activated.partyId(), event.partyId());
        assertEquals(activated.version(), event.partyVersion());
        assertEquals(PROCESS_ID, event.correlationId());
        assertEquals(Map.of("partyType", "NATURAL_PERSON", "status", "ACTIVE"), entity.payload());
        assertEquals(PartyOutboxStatus.PENDING, entity.status());
        assertFalse(Arrays.stream(PartyActivatedOutboxCandidate.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(name -> name.contains("identifier")
                        || name.contains("value")
                        || name.contains("cipher")
                        || name.contains("hash")
                        || name.contains("key")));
    }

    @Test
    void preservesCancellationAndTypedFailuresAndSanitizesUnexpectedFailures() {
        CancellationException cancellation = new CancellationException();
        ApplicationException typed = new ApplicationException(
                new ApplicationFailure.MissingQualifyingIdentifier(PARTY_ID));
        IllegalStateException unexpected = new IllegalStateException("sensitive persistence detail");

        assertSame(cancellation, HibernateReactivePartyActivationAdapter.translateFailure(cancellation));
        assertSame(typed, HibernateReactivePartyActivationAdapter.translateFailure(typed));
        ApplicationException translated = assertInstanceOf(
                ApplicationException.class,
                HibernateReactivePartyActivationAdapter.translateFailure(unexpected));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, translated.failure());
        assertSame(unexpected, translated.getCause());
        assertFalse(translated.getMessage().contains("sensitive"));
    }

    @Test
    void usesOneTransactionalEntryPointAndNoRemoteCollaborators() {
        var adapterClass = new ClassFileImporter()
                .importClasses(
                        HibernateReactivePartyActivationAdapter.class,
                        Mutiny.SessionFactory.class)
                .get(HibernateReactivePartyActivationAdapter.class);
        List<String> sessionFactoryCalls = adapterClass.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().isEquivalentTo(Mutiny.SessionFactory.class))
                .map(call -> call.getTarget().getName())
                .toList();

        assertEquals(1L, sessionFactoryCalls.stream()
                .filter("withTransaction"::equals)
                .count());
        assertEquals(1L, sessionFactoryCalls.stream()
                .filter("withSession"::equals)
                .count());

        Set<Class<?>> collaboratorTypes = Arrays.stream(
                        HibernateReactivePartyActivationAdapter.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                Mutiny.SessionFactory.class,
                NaturalPersonPersistenceMapper.class,
                LegalEntityPersistenceMapper.class,
                PartyIdentifierPersistenceMapper.class,
                IdentifierSchemePersistenceMapper.class,
                PartyOutboxEventPersistenceMapper.class,
                PartyActivationDecision.class,
                OperationObservationPort.class,
                PartyOutboxPersistenceMode.class), collaboratorTypes);
    }

    private static PartyActivationCandidate candidate(PartyVersion expectedVersion) {
        return new PartyActivationCandidate(
                new RequestMetadata(TENANT_ID, USER_ID, PROCESS_ID),
                PARTY_ID,
                expectedVersion,
                LocalDate.of(2026, 9, 4),
                ACTIVATED_AT);
    }

    private static NaturalPerson party(PartyRecordStatus status, PartyVersion version) {
        return NaturalPerson.restore(
                PARTY_ID,
                TENANT_ID,
                "Ada Lovelace",
                status,
                version,
                AuditInfo.initial(CREATED_AT, "creator"),
                new NaturalPersonDetails(
                        "Ada",
                        "Lovelace",
                        null,
                        LocalDate.of(1815, 12, 10),
                        null,
                        "GB"));
    }

    private static ApplicationFailure assertFailure(
            Class<? extends ApplicationFailure> expectedType,
            Runnable action) {
        ApplicationException exception = assertThrows(ApplicationException.class, action::run);
        assertTrue(expectedType.isInstance(exception.failure()));
        return exception.failure();
    }
}
