package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.sequencedPort;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.useCase;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.DisplayName;
import com.alexastudillo.partyregistry.domain.DomainViolation;
import com.alexastudillo.partyregistry.domain.PartyType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PartyDisplayNameMutationContractTest {
    private static final UUID TENANT = UUID.fromString("098f0c72-4a7b-7c91-8b2a-1234567890a1");
    private static final UUID PROCESS = UUID.fromString("098f0c72-4a7b-7c91-8b2a-1234567890a2");
    private static final UUID PARTY = UUID.fromString("098f0c72-4a7b-7c91-8b2a-1234567890a3");
    private static final UUID OUTBOX = UUID.fromString("098f0c72-4a7b-7c91-8b2a-1234567890a4");
    private static final String ACTOR = "synthetic-display-name-actor";

    @Test
    void naturalPersonCreationCarriesCanonicalNameAndMatchingSourcesInOneAtomicIntent() {
        PartyDetailsView details = details(
                DetailKind.NATURAL_PERSON,
                "  María\tJosé ",
                " de\n la   Cruz ",
                "Preferred Alias",
                "EC");

        assertCreation(
                PartyType.NATURAL_PERSON,
                details,
                DisplayName.forNaturalPerson(details.primaryName(), details.secondaryName()),
                "María José de la Cruz");
    }

    @Test
    void legalEntityCreationCarriesCanonicalLegalNameWithoutSubstitutingTradeName() {
        PartyDetailsView details = details(
                DetailKind.LEGAL_ENTITY,
                "  Société\n Générale   S.A. ",
                null,
                "Completely Different Trade Name",
                "EC");

        assertCreation(
                PartyType.LEGAL_ENTITY,
                details,
                DisplayName.forLegalEntity(details.primaryName()),
                "Société Générale S.A.");
    }

    @Test
    void naturalPersonDetailNameUpdateCarriesCanonicalNameSourcesVersionAndOutboxTogether() {
        PartyDetailsView details = details(
                DetailKind.NATURAL_PERSON,
                "  Élodie ",
                " D'Alembert  ",
                "Unrelated Preferred Name",
                "EC");

        assertDetailUpdate(
                details,
                DisplayName.forNaturalPerson(details.primaryName(), details.secondaryName()),
                "Élodie D'Alembert");
    }

    @Test
    void legalEntityDetailNameUpdateCarriesCanonicalLegalNameWithoutSubstitutingTradeName() {
        PartyDetailsView details = details(
                DetailKind.LEGAL_ENTITY,
                "  Compañía\tÚnica   S.A. ",
                null,
                "Unrelated Trade Name",
                "EC");

        assertDetailUpdate(
                details,
                DisplayName.forLegalEntity(details.primaryName()),
                "Compañía Única S.A.");
    }

    @Test
    void creationRejectsBothTypeDetailMismatchesWithoutAUnitOfWorkCall() {
        assertCreationRejectedBeforePersistence(PartyType.NATURAL_PERSON, details(
                DetailKind.LEGAL_ENTITY, "Synthetic Legal Name", null, null, "EC"));
        assertCreationRejectedBeforePersistence(PartyType.LEGAL_ENTITY, details(
                DetailKind.NATURAL_PERSON, "Synthetic", "Person", null, "EC"));
    }

    @Test
    void creationRejectsBlankNaturalPersonDisplayNameSourcesBeforePersistence() {
        assertBlankCreationRejected(details(DetailKind.NATURAL_PERSON, " \t\n ", "Family", null, "EC"));
        assertBlankCreationRejected(details(DetailKind.NATURAL_PERSON, "Given", " \t\n ", null, "EC"));
    }

    @Test
    void detailUpdateRejectsBlankLegalNameBeforePersistence() {
        List<String> sequence = new ArrayList<>();
        var geography = successfulGeography(sequence);
        var unitOfWork = successfulUnitOfWork(sequence, "updateDetailsAndAppendOutbox");
        Object service = useCase("UpdatePartyDetailsUseCase", geography.proxy(), unitOfWork.proxy());

        assertThrows(DomainViolation.class, () -> invoke(service, "execute",
                context(), PARTY, details(DetailKind.LEGAL_ENTITY, " \t\n ", null, "Trade", "EC"), 7L));

        assertEquals(0, unitOfWork.count("updateDetailsAndAppendOutbox"));
    }

    private static void assertCreation(
            PartyType type, PartyDetailsView details, String policyValue, String exactCanonicalValue) {
        assertEquals(exactCanonicalValue, policyValue, "the approved domain policy remains the expected oracle");
        List<String> sequence = new ArrayList<>();
        var geography = successfulGeography(sequence);
        var unitOfWork = sequencedPort("PartyUnitOfWorkPort", sequence)
                .answers("createPartyAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertCommonIntent(intent, 0L, "party.created.v1");
                    assertEquals(type, fact(intent, "type"));
                    assertDetailsAndCanonicalName(intent, details, exactCanonicalValue);
                    return CompletableFuture.completedFuture(mutationResult(0L));
                });

        Object result = invoke(useCase("CreatePartyUseCase", geography.proxy(), unitOfWork.proxy()), "execute",
                context(), new CreatePartyCommand(type, details));

        assertEquals(List.of(
                "GeographicReferencePort.resolveActive",
                "PartyUnitOfWorkPort.createPartyAndAppendOutbox"), sequence);
        assertEquals(1, unitOfWork.count("createPartyAndAppendOutbox"));
        assertEquals(0, unitOfWork.count("updateDetailsAndAppendOutbox"));
        assertNotNull(result);
    }

    private static void assertDetailUpdate(
            PartyDetailsView details, String policyValue, String exactCanonicalValue) {
        assertEquals(exactCanonicalValue, policyValue, "the approved domain policy remains the expected oracle");
        List<String> sequence = new ArrayList<>();
        var geography = successfulGeography(sequence);
        var unitOfWork = sequencedPort("PartyUnitOfWorkPort", sequence)
                .answers("updateDetailsAndAppendOutbox", arguments -> {
                    Object intent = arguments[0];
                    assertCommonIntent(intent, 7L, "party.updated.v1");
                    assertDetailsAndCanonicalName(intent, details, exactCanonicalValue);
                    return CompletableFuture.completedFuture(mutationResult(8L));
                });

        Object result = invoke(useCase("UpdatePartyDetailsUseCase", geography.proxy(), unitOfWork.proxy()), "execute",
                context(), PARTY, details, 7L);

        assertEquals(List.of(
                "GeographicReferencePort.resolveActive",
                "PartyUnitOfWorkPort.updateDetailsAndAppendOutbox"), sequence);
        assertEquals(1, unitOfWork.count("updateDetailsAndAppendOutbox"));
        assertEquals(0, unitOfWork.count("createPartyAndAppendOutbox"));
        assertEquals(8L, invoke(result, "version"));
    }

    private static void assertCommonIntent(Object intent, long expectedVersion, String eventType) {
        assertEquals(TENANT, invoke(intent, "tenantId"));
        assertEquals(PARTY, invoke(intent, "partyId"));
        assertEquals(expectedVersion, invoke(intent, "expectedVersion"));
        assertEquals(ACTOR, invoke(intent, "actorId"));
        Object outbox = invoke(intent, "outbox");
        assertEquals(eventType, invoke(outbox, "eventType"));
        assertEquals(PROCESS, invoke(outbox, "correlationId"));
    }

    private static void assertDetailsAndCanonicalName(
            Object intent, PartyDetailsView details, String exactCanonicalValue) {
        assertEquals(details.kind(), fact(intent, "kind"));
        assertEquals(details.primaryName(), fact(intent, "primaryName"));
        assertEquals(details.secondaryName(), fact(intent, "secondaryName"));
        assertEquals(details.optionalName(), fact(intent, "optionalName"));
        assertEquals(exactCanonicalValue, fact(intent, "canonicalDisplayName", "displayName"));
        if (details.optionalName() != null) {
            assertTrue(!exactCanonicalValue.equals(details.optionalName()),
                    "preferred/trade name fixture must be distinct from the canonical source");
        }
    }

    private static void assertCreationRejectedBeforePersistence(PartyType type, PartyDetailsView details) {
        List<String> sequence = new ArrayList<>();
        var geography = successfulGeography(sequence);
        var unitOfWork = successfulUnitOfWork(sequence, "createPartyAndAppendOutbox");
        Object service = useCase("CreatePartyUseCase", geography.proxy(), unitOfWork.proxy());

        RuntimeException rejection = assertThrows(RuntimeException.class,
                () -> invoke(service, "execute", context(), new CreatePartyCommand(type, details)));

        assertNotNull(rejection.getMessage());
        assertTrue(!rejection.getMessage().isBlank(), "mismatch rejection must remain diagnosable");
        assertEquals(0, unitOfWork.count("createPartyAndAppendOutbox"));
    }

    private static void assertBlankCreationRejected(PartyDetailsView details) {
        List<String> sequence = new ArrayList<>();
        var geography = successfulGeography(sequence);
        var unitOfWork = successfulUnitOfWork(sequence, "createPartyAndAppendOutbox");
        Object service = useCase("CreatePartyUseCase", geography.proxy(), unitOfWork.proxy());

        assertThrows(DomainViolation.class, () -> invoke(service, "execute",
                context(), new CreatePartyCommand(PartyType.NATURAL_PERSON, details)));

        assertEquals(0, unitOfWork.count("createPartyAndAppendOutbox"));
    }

    private static ApplicationContractSupport.RecordingPort successfulGeography(List<String> sequence) {
        return sequencedPort("GeographicReferencePort", sequence)
                .returns("resolveActive", CompletableFuture.completedFuture(
                        value("CountryEvidence", "EC", Instant.parse("2026-08-19T10:00:00Z"))));
    }

    private static ApplicationContractSupport.RecordingPort successfulUnitOfWork(
            List<String> sequence, String method) {
        return sequencedPort("PartyUnitOfWorkPort", sequence)
                .returns(method, CompletableFuture.completedFuture(mutationResult(1L)));
    }

    private static PartyDetailsView details(
            DetailKind kind, String primaryName, String secondaryName, String optionalName, String countryCode) {
        return new PartyDetailsView(
                PARTY,
                kind,
                primaryName,
                secondaryName,
                optionalName,
                countryCode,
                LocalDate.of(2000, 1, 1),
                null);
    }

    private static RequestContext context() {
        return new RequestContext(
                TENANT.toString(), ACTOR, PROCESS.toString(), (Supplier<UUID>) () -> PROCESS);
    }

    private static MutationResult mutationResult(long version) {
        return new MutationResult(PARTY, version, OUTBOX, Instant.parse("2026-08-19T10:00:01Z"));
    }

    private static Object fact(Object target, String... names) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Object found = findFact(target, Set.of(names), visited);
        if (found == MissingFact.INSTANCE) {
            fail(target.getClass().getSimpleName() + " does not carry required application fact "
                    + String.join("/", names));
        }
        return found;
    }

    private static Object findFact(Object target, Set<String> names, Set<Object> visited) {
        if (target == null || !visited.add(target) || !target.getClass().isRecord()) {
            return MissingFact.INSTANCE;
        }
        RecordComponent[] components = target.getClass().getRecordComponents();
        for (RecordComponent component : components) {
            if (names.contains(component.getName())) {
                return read(component.getAccessor(), target);
            }
        }
        for (RecordComponent component : components) {
            Object nested = read(component.getAccessor(), target);
            Object found = findFact(nested, names, visited);
            if (found != MissingFact.INSTANCE) {
                return found;
            }
        }
        return MissingFact.INSTANCE;
    }

    private static Object read(Method accessor, Object target) {
        try {
            return accessor.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect boundary-neutral application record", exception);
        }
    }

    private enum MissingFact {
        INSTANCE
    }
}
