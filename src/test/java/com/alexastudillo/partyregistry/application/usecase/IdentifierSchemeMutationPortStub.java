package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Models sequential Application choreography and acceptance without claiming database concurrency guarantees. */
final class IdentifierSchemeMutationPortStub implements IdentifierSchemeMutationPort {
    final List<String> calls = new ArrayList<>();
    final Map<IdentifierSchemeId, IdentifierScheme> schemes = new HashMap<>();
    final Map<CompletionKey, CompletedIdentifierSchemeOperation> completions = new HashMap<>();
    @Nullable Throwable completionFailure;
    @Nullable RuntimeException writeFailure;
    @Nullable RuntimeException acceptanceFailure;

    @Override
    public Uni<IdentifierSchemeMutationOutcome> execute(RequestMetadata metadata,
            Function<IdentifierSchemeMutationContext, Uni<IdentifierSchemeMutationOutcome>> work) {
        return Uni.createFrom().deferred(() -> {
            calls.add("begin");
            var context = new Context(metadata.tenantId());
            return Uni.createFrom().deferred(() -> work.apply(context))
                    .invoke(_ -> {
                        var failure = acceptanceFailure;
                        if (failure != null) {
                            throw failure;
                        }
                        calls.add("commit");
                        schemes.clear();
                        schemes.putAll(context.pendingSchemes);
                        completions.clear();
                        completions.putAll(context.pendingCompletions);
                    })
                    .onFailure().invoke(() -> calls.add("rollback"))
                    .onCancellation().invoke(() -> calls.add("cancel"))
                    .onTermination().invoke(() -> {
                        context.active = false;
                        calls.add("close");
                    });
        });
    }

    /** Defines full replay identity separately from global catalog ownership. */
    record CompletionKey(TenantId tenantId, String operation, String key) { }

    /** Limits deterministic I/O capabilities to one sequential callback subscription. */
    private final class Context implements IdentifierSchemeMutationContext {
        private final TenantId tenant;
        private final Map<IdentifierSchemeId, IdentifierScheme> pendingSchemes = new HashMap<>(schemes);
        private final Map<CompletionKey, CompletedIdentifierSchemeOperation> pendingCompletions = new HashMap<>(completions);
        private boolean active = true;

        private Context(TenantId tenant) {
            this.tenant = tenant;
        }

        @Override
        public TenantId tenantId() {
            if (!active) {
                throw new IllegalStateException("Mutation context has closed");
            }
            return tenant;
        }

        @Override
        public Uni<Void> serializeReplayKey(String operation, String key) {
            return operation("key", () -> null);
        }

        @Override
        public Uni<Optional<CompletedIdentifierSchemeOperation>> findCompleted(String operation, String key) {
            return operation("completion", () -> Optional.ofNullable(pendingCompletions.get(new CompletionKey(tenant, operation, key))));
        }

        @Override
        public Uni<Void> serializeCode(String code) {
            return operation("code", () -> null);
        }

        @Override
        public Uni<Boolean> codeExists(String code) {
            return operation("exists", () -> pendingSchemes.values().stream().anyMatch(scheme -> scheme.code().equals(code)));
        }

        @Override
        public Uni<Optional<IdentifierScheme>> findForUpdate(IdentifierSchemeId id) {
            return operation("scheme", () -> Optional.ofNullable(pendingSchemes.get(id)));
        }

        @Override
        public Uni<IdentifierScheme> insert(IdentifierScheme candidate) {
            return operation("insert", () -> {
                pendingSchemes.put(candidate.id(), candidate);
                return candidate;
            });
        }

        @Override
        public Uni<IdentifierScheme> persistScheme(IdentifierScheme candidate, IdentifierSchemeVersion expectedVersion) {
            return operation("write", () -> {
                var failure = writeFailure;
                if (failure != null) {
                    throw failure;
                }
                var current = pendingSchemes.get(candidate.id());
                if (current == null || !current.version().equals(expectedVersion)) {
                    throw new IllegalStateException("Test workflow did not retain its locked expected version");
                }
                pendingSchemes.put(candidate.id(), candidate);
                return candidate;
            });
        }

        @Override
        public Uni<Void> recordCompletion(String operation, String key, CompletedIdentifierSchemeOperation completed) {
            return operation("record", () -> {
                var failure = completionFailure;
                if (failure != null) {
                    throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
                }
                pendingCompletions.put(new CompletionKey(tenant, operation, key), completed);
                return null;
            });
        }

        private <T> Uni<T> operation(String name, Supplier<@Nullable T> value) {
            return Uni.createFrom().item(() -> {
                if (!active) {
                    throw new IllegalStateException("Mutation context has closed");
                }
                calls.add(name);
                return value.get();
            });
        }
    }
}
