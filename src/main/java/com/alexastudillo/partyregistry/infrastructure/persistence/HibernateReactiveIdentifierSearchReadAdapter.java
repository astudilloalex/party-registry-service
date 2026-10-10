package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.infrastructure.security.IdentifierSearchCpuDispatcher;
import com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter;
import com.alexastudillo.partyregistry.infrastructure.observability.IdentifierSearchWorkObserver;
import static com.alexastudillo.partyregistry.infrastructure.observability.IdentifierSearchWorkObserver.Measurement.*;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/** Supplies tenant-qualified exact and bounded encrypted-prefix reads in one guarded read-only snapshot per subscription. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveIdentifierSearchReadAdapter implements IdentifierSearchReadPort {
    private final IdentifierSearchReadSession reads;
    private final JcaIdentifierProtectionAdapter protection;
    private final IdentifierSearchCpuDispatcher cpu;
    private final IdentifierSearchSql sql;
    private final IdentifierSearchWorkObserver observations;

    @Inject
    public HibernateReactiveIdentifierSearchReadAdapter(IdentifierSearchReadSession reads, JcaIdentifierProtectionAdapter protection,
            IdentifierSearchCpuDispatcher cpu, IdentifierSearchWorkObserver observations) {
        this.reads = reads;
        this.protection = protection;
        this.cpu = cpu;
        this.observations = observations;
        sql = new IdentifierSearchSql(protection);
    }

    /** Allows isolated adapter tests to supply a local registry without service lookup. */
    public HibernateReactiveIdentifierSearchReadAdapter(IdentifierSearchReadSession reads, JcaIdentifierProtectionAdapter protection,
            IdentifierSearchCpuDispatcher cpu) {
        this(reads, protection, cpu, new IdentifierSearchWorkObserver(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Override
    public <T> Uni<T> withRead(TenantId tenant, Function<IdentifierSearchReadContext, Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            var sample = observations.start();
            return reads.read(view -> work.apply(new ReadContext(tenant, view, sample)))
                    .onTermination().invoke((item, failure, cancelled) -> sample.complete(failure, cancelled));
        });
    }

    /** Retains only the last scanned tuple, including nonmatching batch boundaries, between terminated batch operations. */
    private static final class ScanPosition {
        private Optional<PartyIdentifierPagePosition> after = Optional.empty();
    }

    /** Exposes only Application/Domain contracts while the read owner guards lifetime, thread and sequential session use. */
    private final class ReadContext implements IdentifierSearchReadContext {
        private final TenantId tenant;
        private final IdentifierSearchReadSession.ReadView view;
        private final IdentifierSearchWorkObserver.Sample sample;

        private ReadContext(TenantId tenant, IdentifierSearchReadSession.ReadView view, IdentifierSearchWorkObserver.Sample sample) {
            this.tenant = tenant;
            this.view = view;
            this.sample = sample;
        }

        @Override public Uni<Optional<IdentifierScheme>> findSchemeByCode(String exactCode) {
            sample.add(SQL_STATEMENTS, 1);
            return view.findSchemeByCode(exactCode);
        }

        @Override
        public Uni<ExactIdentifierSearchSlice> findExact(IdentifierSchemeId schemeId, IdentifierSearchSelector selector, IdentifierSearchFilters filters) {
            sample.operation(IdentifierSearchWorkObserver.Operation.EXACT);
            return view.query(session -> sql.exact(session, tenant, schemeId, selector, filters, sample));
        }

        @Override
        public Uni<PrefixIdentifierSearchSlice> findPrefix(IdentifierSearchScope scope, Optional<PartyIdentifierPageBoundary> boundary) {
            sample.operation(IdentifierSearchWorkObserver.Operation.PREFIX);
            return view.query(session -> {
                if (!tenant.equals(scope.tenantId())) return Uni.createFrom().failure(new IllegalStateException("Identifier search scope owner is inconsistent"));
                return prefix(session, scope, boundary);
            });
        }

        private Uni<PrefixIdentifierSearchSlice> prefix(Mutiny.Session session, IdentifierSearchScope scope,
                Optional<PartyIdentifierPageBoundary> boundary) {
            if (scope.filters().matchesNothing()) return Uni.createFrom().item(new PrefixIdentifierSearchSlice(List.of(), 0, Optional.empty(), Optional.empty()));
            var accumulator = new IdentifierSearchPrefixAccumulator(scope.limit(), boundary);
            var scan = new ScanPosition();
            // Repeat a terminated batch Uni rather than recursively retaining prior batch pipelines/closures.
            Uni<Boolean> step = Uni.createFrom().deferred(() -> {
                        sample.add(SQL_STATEMENTS, 1);
                        return sql.candidates(session, scope.tenantId(), scope.schemeId(), scope.filters(), scan.after);
                    })
                    .flatMap(batch -> {
                        sample.add(CANDIDATE_BATCHES, 1);
                        sample.maximum(MAX_BATCH, batch.size());
                        if (batch.isEmpty()) return Uni.createFrom().item(true);
                        return cpu.dispatch(cancelled -> {
                            var matches = new ArrayList<IdentifierSearchHit>();
                            for (var candidate : batch) {
                                if (cancelled.getAsBoolean()) throw new CancellationException("Identifier search cancelled");
                                sample.add(CANDIDATES, 1);
                                if (protection.matchesPrefix(candidate.encryptedValue(), candidate.identity(), scope.selector())) {
                                    matches.add(candidate.hit());
                                    sample.add(MATCHES, 1);
                                }
                            }
                            return List.copyOf(matches);
                        }).map(matches -> {
                            matches.forEach(accumulator::add);
                            sample.maximum(MAX_PAGE, accumulator.retainedSize());
                            scan.after = Optional.of(batch.getLast().hit().position());
                            return batch.size() < IdentifierSearchSql.BATCH_SIZE;
                        });
                    });
            return step.repeat().until(Boolean::booleanValue).collect().last().map(ignored -> accumulator.finish());
        }
    }
}
