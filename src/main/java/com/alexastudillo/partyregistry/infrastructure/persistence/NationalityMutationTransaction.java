package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import com.alexastudillo.partyregistry.domain.policy.NationalityPeriodPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.LockMode;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** Owns one reactive nationality write transaction and translates only named PostgreSQL exclusion failures. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class NationalityMutationTransaction {

    private static final String COUNTRY_CONSTRAINT = "ex_party_nationalities_country_validity";
    private static final String PRIMARY_CONSTRAINT = "ex_party_nationalities_primary_validity";
    private static final String PARAM_TENANT_ID = "tenantId";
    private static final String PARAM_PARTY_ID = "partyId";

    private final Mutiny.SessionFactory sessions;
    private final PartyMutationTimeouts timeouts;
    private final MeterRegistry metrics;

    @Inject
    public NationalityMutationTransaction(Mutiny.SessionFactory sessions, PartyMutationTimeouts timeouts,
            MeterRegistry metrics) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Starts a fresh transaction; callers may acquire a replay-key lock before the Party root lock. */
    <T> Uni<T> execute(RequestMetadata metadata, Function<Scope, Uni<T>> work) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(work, "work");
        return Uni.createFrom().deferred(() -> {
            long started = System.nanoTime();
            return sessions.openSession().flatMap(session ->
                        session.withTransaction(transaction -> timeouts.limit(timeouts.configure(session)
                                .chain(() -> Uni.createFrom().deferred(() -> work.apply(new Scope(session, metadata))))
                                .call(session::flush))).eventually(session::close))
                .onFailure(failure -> NationalityExclusionFailure.isNamed(failure, COUNTRY_CONSTRAINT))
                .transform(failure -> new ApplicationException(new ApplicationFailure.NationalityValidityConflict(), failure))
                .onFailure(failure -> NationalityExclusionFailure.isNamed(failure, PRIMARY_CONSTRAINT))
                .transform(failure -> new ApplicationException(new ApplicationFailure.PrimaryNationalityConflict(), failure))
                .onFailure(DomainValidationException.class).transform(NationalityMutationTransaction::nationalityFailure)
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException)
                .onTermination().invoke((item, failure, cancelled) -> recordTransaction(started, failure, cancelled));
        });
    }

    private void recordTransaction(long started, Throwable failure, boolean cancelled) {
        String outcome = outcome(failure, cancelled);
        try {
            metrics.timer("party.registry.nationality.transaction", "outcome", outcome)
                    .record(Math.max(0L, System.nanoTime() - started), TimeUnit.NANOSECONDS);
        } catch (RuntimeException _) {
            // Recording must not change a committed result, failure, or cancellation.
        }
    }

    private static String outcome(Throwable failure, boolean cancelled) {
        if (cancelled || failure instanceof CancellationException) {
            return "cancelled";
        }
        if (failure == null) {
            return "success";
        }
        if (failure instanceof ApplicationException application) {
            return switch (application.failure()) {
                case ApplicationFailure.NationalityValidityConflict _ -> "country-conflict";
                case ApplicationFailure.PrimaryNationalityConflict _ -> "primary-conflict";
                case ApplicationFailure.IdempotencyKeyConflict _ -> "key-conflict";
                default -> "failure";
            };
        }
        return "failure";
    }

    private static ApplicationException nationalityFailure(DomainValidationException exception) {
        ApplicationFailure failure = switch (exception.violation()) {
            case NATIONALITY_VALIDITY_DATE_ORDER -> new ApplicationFailure.NationalityValidityInvalid();
            case NATIONALITY_VALIDITY_CONFLICT -> new ApplicationFailure.NationalityValidityConflict();
            case PRIMARY_NATIONALITY_CONFLICT -> new ApplicationFailure.PrimaryNationalityConflict();
            default -> null;
        };
        return failure == null ? ApplicationException.of(exception) : new ApplicationException(failure, exception);
    }

    /** Restricts same-session root locking, period inspection, and one version advance to the caller's tenant. */
    static final class Scope {
        private final Mutiny.Session session;
        private final RequestMetadata metadata;
        private PartyEntity lockedRoot;
        private boolean advanced;

        private Scope(Mutiny.Session session, RequestMetadata metadata) {
            this.session = session;
            this.metadata = metadata;
        }

        Mutiny.Session session() {
            return session;
        }

        RequestMetadata metadata() {
            return metadata;
        }

        /** Checks tenant ownership before remote validation without locking the Party root. */
        Uni<Void> verifyParty(PartyId partyId) {
            return session.createQuery("""
                            select party.id from PartyEntity party
                            where party.tenantId = :tenantId and party.id = :partyId
                            """, java.util.UUID.class)
                    .setParameter(PARAM_TENANT_ID, metadata.tenantId().value())
                    .setParameter(PARAM_PARTY_ID, partyId.value())
                    .getSingleResultOrNull()
                    .flatMap(found -> found == null ? Uni.createFrom().failure(new ApplicationException(
                            new ApplicationFailure.PartyNotFound(partyId, metadata.tenantId())))
                            : Uni.createFrom().voidItem());
        }

        /** Acquires the tenant-qualified root row lock after optional replay-key serialization. */
        Uni<Void> lockRoot(PartyId partyId) {
            if (lockedRoot != null) {
                throw new IllegalStateException("One Party root may be locked per nationality transaction");
            }
            return session.createQuery("""
                            from PartyEntity party where party.tenantId = :tenantId and party.id = :partyId
                            """, PartyEntity.class)
                    .setParameter(PARAM_TENANT_ID, metadata.tenantId().value())
                    .setParameter(PARAM_PARTY_ID, partyId.value())
                    .setLockMode(LockMode.PESSIMISTIC_WRITE).getSingleResultOrNull()
                    .flatMap(root -> {
                        if (root == null) {
                            return Uni.createFrom().failure(new ApplicationException(
                                    new ApplicationFailure.PartyNotFound(partyId, metadata.tenantId())));
                        }
                        lockedRoot = root;
                        return Uni.createFrom().voidItem();
                    });
        }

        /** Returns Party-only historical rows after the root lock, without trusting nationality IDs as ownership. */
        Uni<List<PartyNationalityEntity>> history(PartyId partyId) {
            requireRoot(partyId);
            return session.createQuery("""
                            from PartyNationalityEntity nationality where nationality.partyId = :partyId
                            order by nationality.createdAt desc, nationality.id desc
                            """, PartyNationalityEntity.class)
                    .setParameter(PARAM_PARTY_ID, partyId.value()).getResultList();
        }

        /** Applies Domain overlap checks under the lock before inserting or updating a candidate. */
        void validate(PartyNationality candidate, List<PartyNationalityEntity> history) {
            requireRoot(candidate.partyId());
            NationalityPeriodPolicy.validate(candidate, history.stream().map(PartyNationalityEntity::toDomain).toList());
        }

        /** Advances the root version exactly once for a changed accepted nationality mutation. */
        Uni<Void> advanceRoot(PartyId partyId) {
            requireRoot(partyId);
            if (advanced || lockedRoot.version() == Long.MAX_VALUE) {
                throw new IllegalStateException("Nationality mutation cannot advance the Party version twice");
            }
            advanced = true;
            long previous = lockedRoot.version();
            return session.createMutationQuery("""
                            update PartyEntity party set party.version = :nextVersion
                            where party.tenantId = :tenantId and party.id = :partyId and party.version = :previousVersion
                            """)
                    .setParameter("nextVersion", previous + 1)
                    .setParameter("previousVersion", previous)
                    .setParameter(PARAM_TENANT_ID, metadata.tenantId().value())
                    .setParameter(PARAM_PARTY_ID, partyId.value())
                    .executeUpdate().invoke(count -> {
                        if (count != 1) {
                            throw new IllegalStateException("Locked Party version could not advance");
                        }
                    }).replaceWithVoid();
        }

        private void requireRoot(PartyId partyId) {
            if (lockedRoot == null || !lockedRoot.id().equals(partyId.value())
                    || !lockedRoot.tenantId().equals(metadata.tenantId().value())) {
                throw new IllegalStateException("Tenant-owned Party root must be locked before nationality changes");
            }
        }
    }
}
