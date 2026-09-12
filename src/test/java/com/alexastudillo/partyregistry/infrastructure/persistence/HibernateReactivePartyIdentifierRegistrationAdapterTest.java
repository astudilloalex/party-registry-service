package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.vertx.pgclient.PgException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies additional-identifier adapter structure and exact failure classification.
 */
class HibernateReactivePartyIdentifierRegistrationAdapterTest {

    private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(UUID.randomUUID());

    @Test
    void onlyReadsPartyTypeAndHasNoPartyOrDetailPersistenceCollaborator() {
        Set<Class<?>> forbiddenTypes = Set.of(
                NaturalPersonPersistenceMapper.class,
                LegalEntityPersistenceMapper.class,
                NaturalPersonDetailsEntity.class,
                LegalEntityDetailsEntity.class);

        assertFalse(Arrays.stream(
                        HibernateReactivePartyIdentifierRegistrationAdapter.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(forbiddenTypes::contains));

        var adapterClass = new ClassFileImporter()
                .importClasses(
                        HibernateReactivePartyIdentifierRegistrationAdapter.class,
                        PartyEntity.class)
                .get(HibernateReactivePartyIdentifierRegistrationAdapter.class);
        Set<String> partyMethodCalls = adapterClass.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().isEquivalentTo(PartyEntity.class))
                .map(call -> call.getTarget().getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("type"), partyMethodCalls);
    }

    @Test
    void classifiesOnlyTheExactActiveIdentifierConstraintAsAConflict() {
        Throwable translated = HibernateReactivePartyIdentifierRegistrationAdapter.translateFailure(
                postgresFailure("23505", "uq_party_identifiers_active_value"),
                SCHEME_ID);

        ApplicationException exception = assertInstanceOf(ApplicationException.class, translated);
        ApplicationFailure.IdentifierUniquenessConflict conflict = assertInstanceOf(
                ApplicationFailure.IdentifierUniquenessConflict.class,
                exception.failure());
        assertTrue(conflict.schemeId().equals(SCHEME_ID));

        assertSanitizedPersistenceFailure(postgresFailure(
                "23505",
                "uq_party_identifiers_verified_primary"));
        assertSanitizedPersistenceFailure(postgresFailure(
                "23514",
                "uq_party_identifiers_active_value"));
    }

    @Test
    void preservesCancellationAndTypedApplicationFailures() {
        CancellationException cancellation = new CancellationException();
        ApplicationException applicationException = new ApplicationException(
                new ApplicationFailure.PartyNotFound(
                        new com.alexastudillo.partyregistry.domain.model.PartyId(UUID.randomUUID()),
                        new com.alexastudillo.partyregistry.domain.model.TenantId(UUID.randomUUID())));

        assertSame(cancellation,
                HibernateReactivePartyIdentifierRegistrationAdapter.translateFailure(
                        cancellation,
                        SCHEME_ID));
        assertSame(applicationException,
                HibernateReactivePartyIdentifierRegistrationAdapter.translateFailure(
                        applicationException,
                        SCHEME_ID));
    }

    private static void assertSanitizedPersistenceFailure(Throwable failure) {
        ApplicationException translated = assertInstanceOf(
                ApplicationException.class,
                HibernateReactivePartyIdentifierRegistrationAdapter.translateFailure(
                        failure,
                        SCHEME_ID));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, translated.failure());
        assertFalse(translated.getMessage().contains("sensitive"));
    }

    private static PgException postgresFailure(String sqlState, String constraint) {
        return new PgException(
                "sensitive database failure",
                "ERROR",
                sqlState,
                "sensitive detail",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "public",
                "party_identifiers",
                null,
                null,
                constraint);
    }
}
