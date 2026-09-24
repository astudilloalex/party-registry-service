package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationEvidence;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.LockMode;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Provides tenant-qualified locking, minimal evidence, and exact root-only writes within an existing mutation transaction. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyRootMutationPersistence {

    private static final String TENANT = "tenantId";
    private static final String PARTY_ID = "partyId";
    private static final String WRITE_SUFFIX = """
            , party.updatedAt = :updatedAt, party.updatedBy = :updatedBy, party.version = :nextVersion
            where party.tenantId = :tenantId and party.id = :partyId and party.version = :expectedVersion
            """;
    private final PartyRootReader reader;

    /** Reuses the safe same-session subtype reader without exposing managed entities to the workflow. */
    @Inject
    public PartyRootMutationPersistence(PartyRootReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    Uni<Optional<Party>> findForUpdate(Mutiny.Session session, TenantId tenant, PartyId partyId) {
        return session.createQuery("from PartyEntity party where party.tenantId = :tenantId and party.id = :partyId", PartyEntity.class)
                .setParameter(TENANT, tenant.value()).setParameter(PARTY_ID, partyId.value())
                .setLockMode(LockMode.PESSIMISTIC_WRITE).getSingleResultOrNull()
                .flatMap(root -> root == null ? Uni.createFrom().item(Optional.<Party>empty())
                        : reload(session, tenant, partyId).map(Optional::of))
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    Uni<List<PartyActivationEvidence>> activationEvidence(Mutiny.Session session, TenantId tenant, PartyId partyId) {
        return session.createQuery("""
                select identifier.tenantId, identifier.partyId, identifier.identifierSchemeId,
                       scheme.id, identifier.status, identifier.expiresOn, scheme.applicableSubjectType
                from PartyIdentifierEntity identifier join IdentifierSchemeEntity scheme on scheme.id = identifier.identifierSchemeId
                where identifier.tenantId = :tenantId and identifier.partyId = :partyId
                """, Object[].class)
                .setParameter(TENANT, tenant.value()).setParameter(PARTY_ID, partyId.value()).getResultList()
                .map(rows -> rows.stream().map(row -> new PartyActivationEvidence(new TenantId((UUID) row[0]),
                        new PartyId((UUID) row[1]), new IdentifierSchemeId((UUID) row[2]), new IdentifierSchemeId((UUID) row[3]),
                        (PartyIdentifierStatus) row[4], (LocalDate) row[5], (IdentifierSubjectType) row[6])).toList());
    }

    /** Returns a generic expected-version conflict; Application selects the operation-specific public failure semantics. */
    Uni<Party> persistRoot(Mutiny.Session session, TenantId tenant, Party locked, Party candidate, PartyVersion expected) {
        return Uni.createFrom().deferred(() -> {
            validateCandidate(tenant, locked, candidate, expected);
            boolean lifecycle = candidate.recordStatus() != locked.recordStatus();
            String assignment = lifecycle ? "party.recordStatus = :value" : "party.displayName = :value";
            Object value = lifecycle ? candidate.recordStatus() : candidate.displayName();
            return session.createMutationQuery("update PartyEntity party set " + assignment + WRITE_SUFFIX)
                    .setParameter("value", value).setParameter("updatedAt", candidate.auditInfo().updatedAt())
                    .setParameter("updatedBy", candidate.auditInfo().updatedBy())
                    .setParameter("nextVersion", candidate.version().value()).setParameter("expectedVersion", expected.value())
                    .setParameter(TENANT, tenant.value()).setParameter(PARTY_ID, candidate.partyId().value())
                    .executeUpdate().invoke(ignored -> session.clear())
                    .flatMap(count -> count == 1 ? reload(session, tenant, candidate.partyId())
                            : diagnoseUnappliedWrite(session, tenant, candidate.partyId(), expected))
                    .invoke(actual -> {
                        if (!PartyDetailsResult.fromAggregate(candidate).equals(PartyDetailsResult.fromAggregate(actual))) {
                            throw new IllegalStateException("Stored root does not match the accepted candidate");
                        }
                    });
        }).onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    private Uni<Party> reload(Mutiny.Session session, TenantId tenant, PartyId partyId) {
        return reader.read(session, tenant, partyId)
                .map(party -> party.orElseThrow(() -> new IllegalStateException("Locked Party cannot be reloaded")));
    }

    private static Uni<Party> diagnoseUnappliedWrite(Mutiny.Session session, TenantId tenant, PartyId partyId, PartyVersion expected) {
        return session.createSelectionQuery("""
                select party.version from PartyEntity party where party.tenantId = :tenantId and party.id = :partyId
                """, Long.class).setParameter(TENANT, tenant.value()).setParameter(PARTY_ID, partyId.value())
                .getSingleResultOrNull().flatMap(current -> {
                    if (current == null) {
                        return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.PartyNotFound(partyId, tenant)));
                    }
                    if (current != expected.value()) {
                        return Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.ExpectedVersionMismatch(expected, new PartyVersion(current))));
                    }
                    return Uni.createFrom().failure(new IllegalStateException("Conditional root write affected an unexpected row count"));
                });
    }

    private static void validateCandidate(TenantId tenant, Party locked, Party candidate, PartyVersion expected) {
        if (!tenant.equals(locked.tenantId()) || !tenant.equals(candidate.tenantId())
                || !locked.partyId().equals(candidate.partyId()) || locked.type() != candidate.type()
                || expected.value() == Long.MAX_VALUE || candidate.version().value() != expected.value() + 1
                || !locked.auditInfo().createdAt().equals(candidate.auditInfo().createdAt())
                || !locked.auditInfo().createdBy().equals(candidate.auditInfo().createdBy())
                || candidate.auditInfo().updatedAt().getNano() % 1_000 != 0 || !sameDetails(locked, candidate)) {
            throw new IllegalArgumentException("Root candidate does not match its locked persistence scope");
        }
        if (locked.recordStatus() != candidate.recordStatus() && !locked.displayName().equals(candidate.displayName())) {
            throw new IllegalArgumentException("A root write cannot change both lifecycle and display name");
        }
    }

    private static boolean sameDetails(Party locked, Party candidate) {
        return switch (locked) {
            case NaturalPerson natural -> candidate instanceof NaturalPerson next && natural.details().equals(next.details());
            case LegalEntity legal -> candidate instanceof LegalEntity next && legal.details().equals(next.details());
        };
    }
}
