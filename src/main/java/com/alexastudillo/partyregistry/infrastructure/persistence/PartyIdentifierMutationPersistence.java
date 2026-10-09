package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierPrimaryPlan;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

/** Persists Domain-validated identifier candidates with qualified version guards and refreshed safe results. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierMutationPersistence {

    private static final String UPDATE = """
            UPDATE PartyIdentifierEntity identifier SET issuerCode = :issuer, issuedOn = :issued, expiresOn = :expires,
                status = :status, primary = :primary, verifiedAt = :verifiedAt, verifiedBy = :verifiedBy,
                version = :nextVersion, updatedAt = :updatedAt, updatedBy = :updatedBy
            WHERE tenantId = :tenant AND partyId = :party AND id = :identifier AND identifierSchemeId = :scheme AND version = :expected
            """;
    private static final String DUPLICATE_STATE = "23505";
    private final PartyIdentifierPersistenceMapper mapper;
    private final PartyIdentifierMutationReader reader;

    @Inject
    public PartyIdentifierMutationPersistence(PartyIdentifierPersistenceMapper mapper, PartyIdentifierMutationReader reader) {
        this.mapper = mapper;
        this.reader = reader;
    }

    Uni<PartyIdentifierResult> insertIdentifier(Mutiny.Session session, PartyIdentifier candidate) {
        return Uni.createFrom().deferred(() -> {
            validateInitial(candidate);
            return session.persist(mapper.toEntity(candidate)).call(session::flush).invoke(session::clear)
                    .chain(() -> reload(session, candidate));
        }).onFailure(PartyIdentifierMutationPersistence::knownConstraint).transform(failure -> translate(failure, candidate));
    }

    Uni<PartyIdentifierResult> updateIdentifier(Mutiny.Session session, PartyIdentifier candidate, PartyIdentifierVersion expected) {
        return reader.findForUpdate(session, scope(candidate), candidate.identifierId().value()).flatMap(source -> {
            var locked = source.orElseThrow(() -> new IllegalStateException("Identifier write scope is inconsistent"));
            if (!locked.version().equals(expected)) {
                throw new ApplicationException(new ApplicationFailure.IdentifierVersionMismatch(expected, locked.version()));
            }
            validateCandidate(locked, candidate, expected);
            // Explicit bulk version assignment avoids dirty-entity @Version increments.
            return session.createMutationQuery(UPDATE).setParameter("issuer", candidate.issuerCode()).setParameter("issued", candidate.issuedOn())
                    .setParameter("expires", candidate.expiresOn()).setParameter("status", candidate.status()).setParameter("primary", candidate.isPrimary())
                    .setParameter("verifiedAt", candidate.verifiedAt()).setParameter("verifiedBy", candidate.verifiedBy())
                    .setParameter("nextVersion", candidate.version().value()).setParameter("updatedAt", candidate.auditInfo().updatedAt())
                    .setParameter("updatedBy", candidate.auditInfo().updatedBy()).setParameter("tenant", candidate.tenantId().value())
                    .setParameter("party", candidate.partyId().value()).setParameter("identifier", candidate.identifierId().value())
                    .setParameter("scheme", candidate.identifierSchemeId().value()).setParameter("expected", expected.value()).executeUpdate()
                    .invoke(session::clear).chain(count -> count == 1 ? reload(session, candidate) : diagnoseUnapplied(session, candidate, expected));
        }).onFailure(PartyIdentifierMutationPersistence::knownConstraint).transform(failure -> translate(failure, candidate));
    }

    Uni<PartyIdentifierResult> persistPrimaryPlan(Mutiny.Session session, PartyIdentifierPrimaryPlan plan) {
        var selected = plan.candidate();
        var former = plan.formerPrimary();
        Uni<Void> demotion = former == null ? Uni.createFrom().voidItem() : updateIdentifier(session, former, previous(former)).replaceWithVoid();
        return demotion.chain(() -> updateIdentifier(session, selected, previous(selected)));
    }

    private Uni<PartyIdentifierResult> diagnoseUnapplied(Mutiny.Session session, PartyIdentifier candidate, PartyIdentifierVersion expected) {
        return reader.findForUpdate(session, scope(candidate), candidate.identifierId().value()).flatMap(source -> {
            if (source.isPresent() && !source.orElseThrow().version().equals(expected)) {
                return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.IdentifierVersionMismatch(expected, source.orElseThrow().version())));
            }
            return Uni.createFrom().failure(new IllegalStateException("Identifier conditional write count is inconsistent"));
        });
    }

    private Uni<PartyIdentifierResult> reload(Mutiny.Session session, PartyIdentifier candidate) {
        return reader.findForUpdate(session, scope(candidate), candidate.identifierId().value()).flatMap(source -> {
            var actual = source.orElseThrow(() -> new IllegalStateException("Written identifier cannot be reloaded"));
            return session.createSelectionQuery("SELECT scheme.code FROM IdentifierSchemeEntity scheme WHERE scheme.id = :scheme", String.class)
                    .setParameter("scheme", candidate.identifierSchemeId().value()).getSingleResult().map(code -> {
                        var expected = result(candidate, code);
                        var stored = result(actual, code);
                        if (!expected.equals(stored) || !candidate.protectedValue().equals(actual.protectedValue()) || !candidate.auditInfo().equals(actual.auditInfo())) {
                            throw new IllegalStateException("Stored identifier does not match the accepted candidate");
                        }
                        return stored;
                    });
        });
    }

    private static PartyIdentifierResult result(PartyIdentifier identifier, String code) {
        return new PartyIdentifierResult(identifier.identifierId(), identifier.partyId(), identifier.identifierSchemeId(), code,
                identifier.protectedValue().maskedValue(), identifier.status(), identifier.isPrimary(), identifier.issuerCode(), identifier.issuedOn(),
                identifier.expiresOn(), identifier.verifiedAt(), identifier.verifiedBy(), identifier.version(), identifier.auditInfo().createdAt(), identifier.auditInfo().updatedAt());
    }

    private static void validateCandidate(PartyIdentifier source, PartyIdentifier candidate, PartyIdentifierVersion expected) {
        if (!source.protectedValue().equals(candidate.protectedValue()) || !source.auditInfo().createdAt().equals(candidate.auditInfo().createdAt())
                || !source.auditInfo().createdBy().equals(candidate.auditInfo().createdBy()) || expected.value() == Long.MAX_VALUE
                || candidate.version().value() != expected.value() + 1 || candidate.auditInfo().updatedAt().getNano() % 1000 != 0) {
            throw new IllegalStateException("Identifier candidate does not preserve protected history and exact next version");
        }
    }

    private static void validateInitial(PartyIdentifier candidate) {
        var audit = candidate.auditInfo();
        if (candidate.version().value() != 0 || candidate.status() != PartyIdentifierStatus.PENDING_VERIFICATION
                || !audit.createdAt().equals(audit.updatedAt()) || !audit.createdBy().equals(audit.updatedBy())
                || audit.createdAt().getNano() % 1000 != 0 || candidate.verifiedAt() != null || candidate.verifiedBy() != null) {
            throw new IllegalStateException("Inserted identifier must be initial microsecond-attributed evidence");
        }
    }

    private static PartyIdentifierMutationLocks.Scope scope(PartyIdentifier candidate) {
        return new PartyIdentifierMutationLocks.Scope(candidate.tenantId().value(), candidate.partyId().value(), candidate.identifierSchemeId().value());
    }

    private static PartyIdentifierVersion previous(PartyIdentifier candidate) { return new PartyIdentifierVersion(candidate.version().value() - 1); }

    private static boolean knownConstraint(Throwable failure) {
        return PersistenceExceptionTranslator.isConstraint(failure, DUPLICATE_STATE, "uq_party_identifiers_active_value")
                || PersistenceExceptionTranslator.isConstraint(failure, DUPLICATE_STATE, "uq_party_identifiers_verified_primary");
    }

    private static ApplicationException translate(Throwable failure, PartyIdentifier candidate) {
        ApplicationFailure known = PersistenceExceptionTranslator.isConstraint(failure, DUPLICATE_STATE, "uq_party_identifiers_active_value")
                ? new ApplicationFailure.IdentifierUniquenessConflict(candidate.identifierSchemeId()) : new ApplicationFailure.PrimaryIdentifierConflict();
        return new ApplicationException(known, failure);
    }
}
