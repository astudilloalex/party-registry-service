package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises scoped atomic catalog workflows using only inner-layer contracts and deterministic doubles. */
class IdentifierSchemePortContractTest {
    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "operator", UUID.randomUUID());

    @Test
    void workflowIsLazySequentialAndEmitsOnlyAfterAtomicAcceptance() {
        var port = new IdentifierSchemeMutationPortStub();
        var scheme = scheme();
        var completion = new CompletedIdentifierSchemeOperation(input(), IdentifierSchemeResult.fromAggregate(scheme));
        var escaped = new AtomicReference<IdentifierSchemeMutationContext>();
        var operation = port.execute(METADATA, context -> {
            escaped.set(context);
            assertEquals(METADATA.tenantId(), context.tenantId());
            return context.serializeReplayKey(input().operation(), "key")
                    .chain(() -> context.findCompleted(input().operation(), "key"))
                    .chain(found -> {
                        assertTrue(found.isEmpty());
                        return context.serializeCode(scheme.code());
                    })
                    .chain(() -> context.codeExists(scheme.code()))
                    .chain(exists -> {
                        assertFalse(exists);
                        return context.insert(scheme);
                    })
                    .call(_ -> context.recordCompletion(input().operation(), "key", completion))
                    .map(accepted -> new IdentifierSchemeMutationOutcome(IdentifierSchemeResult.fromAggregate(accepted),
                            IdentifierSchemeMutationOutcome.Disposition.APPLIED));
        });
        assertTrue(port.calls.isEmpty());
        var result = operation.invoke(_ -> {
            assertEquals(1, port.schemes.size());
            assertEquals(1, port.completions.size());
        }).await().atMost(WAIT);
        assertEquals(scheme.id(), result.scheme().id());
        assertEquals(List.of("begin", "key", "completion", "code", "exists", "insert", "record", "commit", "close"), port.calls);
        var escapedContext = escaped.get();
        var schemeId = scheme.id();
        var escapedExecution = escapedContext.findForUpdate(schemeId).await();
        assertThrows(IllegalStateException.class, () -> escapedExecution.atMost(WAIT));
    }

    @Test
    void failedCompletionLeavesNeitherSchemeNorSavedResult() {
        var port = new IdentifierSchemeMutationPortStub();
        var failure = new IllegalStateException("injected completion rejection");
        port.completionFailure = failure;
        var scheme = scheme();
        var operation = port.execute(METADATA, context -> context.insert(scheme)
                .call(_ -> context.recordCompletion(input().operation(), "key",
                        new CompletedIdentifierSchemeOperation(input(), IdentifierSchemeResult.fromAggregate(scheme))))
                .map(accepted -> new IdentifierSchemeMutationOutcome(IdentifierSchemeResult.fromAggregate(accepted),
                        IdentifierSchemeMutationOutcome.Disposition.APPLIED)));
        var execution = operation.await();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> execution.atMost(WAIT)));
        assertTrue(port.schemes.isEmpty());
        assertTrue(port.completions.isEmpty());
        assertEquals(List.of("begin", "insert", "record", "rollback", "close"), port.calls);
    }

    @Test
    void cancellationClosesTheScopeWithoutAcceptingPendingWrites() {
        var port = new IdentifierSchemeMutationPortStub();
        var scheme = scheme();
        var operation = port.execute(METADATA, context -> context.insert(scheme)
                .chain(() -> Uni.createFrom().<IdentifierSchemeMutationOutcome>nothing()));
        var subscriber = operation.subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.cancel();
        assertTrue(port.schemes.isEmpty());
        assertTrue(port.completions.isEmpty());
        assertEquals(List.of("begin", "insert", "close", "cancel"), port.calls);
    }

    @Test
    void completionScopeUsesTenantAndOperationWhileSchemeLockRemainsGlobal() {
        var port = new IdentifierSchemeMutationPortStub();
        var scheme = scheme();
        port.schemes.put(scheme.id(), scheme);
        var saved = new CompletedIdentifierSchemeOperation(input(), IdentifierSchemeResult.fromAggregate(scheme));
        port.completions.put(new IdentifierSchemeMutationPortStub.CompletionKey(METADATA.tenantId(), input().operation(), "key"), saved);
        var other = new RequestMetadata(new TenantId(UUID.randomUUID()), "another", UUID.randomUUID());
        port.execute(other, context -> context.findCompleted(input().operation(), "key")
                .chain(found -> {
                    assertTrue(found.isEmpty());
                    return context.findForUpdate(scheme.id());
                }).map(current -> {
                    assertEquals(Optional.of(scheme), current);
                    return new IdentifierSchemeMutationOutcome(IdentifierSchemeResult.fromAggregate(current.orElseThrow()),
                            IdentifierSchemeMutationOutcome.Disposition.REPLAYED);
                })).await().atMost(WAIT);
        port.execute(METADATA, context -> context.findCompleted(IdentifierSchemeLifecycleAction.ACTIVATE.operation(), "key")
                .map(found -> {
                    assertTrue(found.isEmpty());
                    return new IdentifierSchemeMutationOutcome(saved.result(), IdentifierSchemeMutationOutcome.Disposition.REPLAYED);
                })).await().atMost(WAIT);
        assertEquals(1, port.completions.size());
    }

    @Test
    void readAndCursorPortsCarryDetachedRowsAndScopedNavigationWithoutStorageTypes() {
        var scheme = scheme();
        var result = IdentifierSchemeResult.fromAggregate(scheme);
        var criteria = new IdentifierSchemeSearchCriteria(null, null, null, null, 50);
        var scope = new IdentifierSchemeSearchScope(METADATA.tenantId(), criteria);
        var boundary = new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT,
                new IdentifierSchemePagePosition(result.createdAt(), result.id()));
        IdentifierSchemeCursorPort cursors = new IdentifierSchemeCursorPort() {
            @Override
            public IdentifierSchemePageBoundary decode(String token, IdentifierSchemeSearchScope expectedScope) {
                if (!token.equals("saved-position") || !scope.equals(expectedScope)) {
                    throw new IllegalArgumentException("Test cursor scope mismatch");
                }
                return boundary;
            }

            @Override
            public String encode(IdentifierSchemePageBoundary requested, IdentifierSchemeSearchScope requestedScope) {
                assertEquals(boundary, requested);
                assertEquals(scope, requestedScope);
                return "saved-position";
            }
        };
        IdentifierSchemeReadPort reads = new IdentifierSchemeReadPort() {
            @Override
            public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
                return Uni.createFrom().item(id.equals(result.id()) ? Optional.of(result) : Optional.empty());
            }

            @Override
            public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) {
                return Uni.createFrom().item(code.equals(result.code()) ? Optional.of(result) : Optional.empty());
            }

            @Override
            public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria requested,
                    Optional<IdentifierSchemePageBoundary> continuation) {
                assertEquals(criteria, requested);
                assertEquals(Optional.of(boundary), continuation);
                return Uni.createFrom().item(new IdentifierSchemePageSlice(List.of(result), Optional.empty(), Optional.empty()));
            }
        };
        assertEquals(Optional.of(result), reads.findById(result.id()).await().atMost(WAIT));
        assertTrue(reads.findById(new IdentifierSchemeId(UUID.randomUUID())).await().atMost(WAIT).isEmpty());
        assertEquals(Optional.of(result), reads.findByCode("Exact_Code").await().atMost(WAIT));
        assertTrue(reads.findByCode("EXACT_CODE").await().atMost(WAIT).isEmpty());
        var decoded = cursors.decode(cursors.encode(boundary, scope), scope);
        assertEquals(List.of(result), reads.findPage(criteria, Optional.of(decoded)).await().atMost(WAIT).items());
        var otherScope = new IdentifierSchemeSearchScope(new TenantId(UUID.randomUUID()), criteria);
        assertThrows(IllegalArgumentException.class, () -> cursors.decode("saved-position", otherScope));
    }

    static IdentifierScheme scheme() {
        return IdentifierScheme.create(new IdentifierSchemeId(UUID.randomUUID()), "Exact_Code", "EC", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, "Scheme", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false,
                Instant.parse("2026-09-30T00:00:00Z"), "creator");
    }

    static IdentifierSchemeCreateInput input() {
        return new IdentifierSchemeCreateInput("Exact_Code", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                "Scheme", null, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false);
    }
}
