package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.infrastructure.persistence.IdentifierSchemeCoordinatedPort;
import com.alexastudillo.partyregistry.infrastructure.persistence.IdentifierSchemeCoordinatedPort.Mode;
import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCase;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/** Proves keyed convergence, global uniqueness, complete competing revisions, rollback, and real timeout envelopes through HTTP/PostgreSQL. */
@QuarkusTest
@TestProfile(IdentifierSchemeConcurrencyContractTest.Profile.class)
@Timeout(120)
class IdentifierSchemeConcurrencyContractTest {
    @Inject IdentifierSchemeCoordinatedPort control;
    @Inject Mutiny.SessionFactory sessions;
    @Inject Vertx vertx;
    @Inject ChangeIdentifierSchemeLifecycleUseCase lifecycleUseCase;
    @TestHTTPResource URI server;

    /** Selects only coordinated real persistence ports and enables deterministic administrative outbox regression checks. */
    public static final class Profile implements QuarkusTestProfile {
        @Override public Set<Class<?>> getEnabledAlternatives() { return Set.of(IdentifierSchemeCoordinatedPort.class); }
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rabbitmq.devservices.enabled", "false", "party-registry.outbox.mode", "stored-only");
        }
    }

    @AfterEach void releaseBarriers() { control.clear(); }

    @Test @RunOnVertxContext
    void concurrentCreateDefaultsConvergeAndConflictingPayloadsLeaveOneOriginal(UniAsserter asserter) {
        for (String change : List.of("defaults", "name", "code", "configuration")) {
            var winner = actor(Context.fresh(), "original-creator");
            var retry = actor(winner, "contender");
            String code = uniqueCode();
            String key = UUID.randomUUID().toString();
            String input = body(code);
            var original = new AtomicReference<Map<String, Object>>();
            String changed = switch (change) {
                case "defaults" -> input.replace("\"name\":", "\"description\":null,\"minimumLength\":null,\"maximumLength\":null,\"requiresExpiration\":false,\"name\":");
                case "name" -> input.replace("Exact Mixed Name", "Conflicting Name");
                case "code" -> input.replace(code, uniqueCode());
                default -> input.replace("ALPHANUMERIC_V1", "UNSUPPORTED_V1");
            };
            asserter.assertThat(() -> race(winner, () -> post(winner, input, key), () -> post(retry, changed, key),
                    () -> control.awaitKeyWait(winner.tenant(), "identifier-scheme.create.v1", key)), responses -> {
                var accepted = success(responses.getFirst(), 201, winner);
                original.set(accepted);
                if (change.equals("defaults")) assertEquals(accepted, success(responses.getLast(), 201, retry));
                else error(responses.getLast(), 409, "idempotency-key-conflict", retry);
            });
            asserter.assertThat(() -> counts(winner, code), actual -> assertEquals(List.of(1L, 1L, 0L), actual));
            asserter.assertThat(() -> post(retry, input, key), response -> assertEquals(original.get(), success(response, 201, retry)));
            asserter.assertThat(() -> completion(winner, "create", key), records -> assertSnapshot(original.get(), records, "original-creator", "original-creator"));
        }
    }

    @Test @RunOnVertxContext
    void eachLifecycleActionConvergesAndSameKeyTargetOrVersionConflicts(UniAsserter asserter) {
        for (String action : List.of("activate", "deprecate", "retire")) {
            for (String intent : List.of("equivalent", "target", "version")) {
                var winner = actor(Context.fresh(), "lifecycle-winner");
                var retry = actor(winner, "retry-actor");
                var source = new AtomicReference<Map<String, Object>>();
                var other = new AtomicReference<Map<String, Object>>();
                var accepted = new AtomicReference<Map<String, Object>>();
                String key = UUID.randomUUID().toString();
                asserter.execute(() -> prepare(winner, action.equals("deprecate") ? "ACTIVE" : "DRAFT").invoke(source::set));
                asserter.execute(() -> prepare(winner, action.equals("deprecate") ? "ACTIVE" : "DRAFT").invoke(other::set));
                asserter.assertThat(() -> race(winner,
                        () -> lifecycle(winner, source.get(), action, key, version(source.get())),
                        () -> lifecycle(retry, intent.equals("target") ? other.get() : source.get(), action, key,
                                version(source.get()) + (intent.equals("version") ? 1 : 0)),
                        () -> control.awaitKeyWait(winner.tenant(), "identifier-scheme." + action + ".v1", key)), responses -> {
                    accepted.set(success(responses.getFirst(), 200, winner));
                    if (intent.equals("equivalent")) assertEquals(accepted.get(), success(responses.getLast(), 200, retry));
                    else error(responses.getLast(), 409, "idempotency-key-conflict", retry);
                });
                asserter.assertThat(() -> get(retry, other.get()), response -> assertEquals(other.get(), success(response, 200, retry)));
                asserter.assertThat(() -> row(source.get()), stored -> assertRow(accepted.get(), stored, "lifecycle-winner", "lifecycle-winner"));
                asserter.assertThat(() -> completion(winner, action, key), stored -> assertSnapshot(accepted.get(), stored, "lifecycle-winner", "lifecycle-winner"));
                if (!action.equals("retire")) {
                    asserter.execute(() -> lifecycle(retry, accepted.get(), "retire", null, version(accepted.get()))
                            .invoke(response -> success(response, 200, retry)));
                }
                asserter.assertThat(() -> lifecycle(retry, source.get(), action, key, version(source.get())),
                        response -> assertEquals(accepted.get(), success(response, 200, retry)));
                var anotherTenant = Context.fresh();
                asserter.assertThat(() -> lifecycle(anotherTenant, source.get(), action, key, version(source.get())),
                        response -> error(response, 412, "expected-version-mismatch", anotherTenant));
                asserter.assertThat(() -> completion(anotherTenant, action, key), records -> assertTrue(records.isEmpty()));
            }
        }
    }

    @Test @RunOnVertxContext
    void tenantAndActionNamespacesProgressIndependentlyWithTheSameExactKey(UniAsserter asserter) {
        var first = Context.fresh();
        var second = Context.fresh();
        String key = "shared-" + UUID.randomUUID();
        var a = new AtomicReference<Map<String, Object>>();
        var b = new AtomicReference<Map<String, Object>>();
        var probe = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
        asserter.execute(() -> post(first, body(uniqueCode()), key).invoke(response -> a.set(success(response, 201, first))));
        asserter.execute(() -> post(second, body(uniqueCode()), key).invoke(response -> b.set(success(response, 201, second))));
        asserter.assertThat(() -> {
            probe.set(control.arm(UUID.fromString(first.process()), Mode.HOLD));
            var held = lifecycle(first, a.get(), "activate", key, 0);
            var independent = probe.get().reached().chain(() -> lifecycle(second, b.get(), "activate", key, 0))
                    .invoke(response -> { success(response, 200, second); probe.get().release(); });
            return Uni.combine().all().unis(held, independent).asTuple().eventually(() -> probe.get().release());
        }, responses -> a.set(success(responses.getItem1(), 200, first)));
        asserter.execute(control::clear);
        asserter.execute(() -> lifecycle(first, a.get(), "deprecate", key, 1).invoke(response -> a.set(success(response, 200, first))));
        asserter.execute(() -> lifecycle(first, a.get(), "retire", key, 2).invoke(response -> a.set(success(response, 200, first))));
        asserter.assertThat(() -> counts(first, (String) a.get().get("code")), actual -> assertEquals(List.of(1L, 4L, 0L), actual));
        asserter.assertThat(() -> counts(second, (String) b.get().get("code")), actual -> assertEquals(List.of(1L, 2L, 0L), actual));
    }

    @Test @RunOnVertxContext
    void globalCodeContendersConflictBeforeInvalidConfigurationAndCannotSaveLosingReplay(UniAsserter asserter) {
        for (boolean crossTenant : List.of(false, true)) {
            var winner = Context.fresh();
            var loser = crossTenant ? Context.fresh() : actor(winner, "independent-key");
            String code = uniqueCode();
            String winningKey = UUID.randomUUID().toString();
            String losingKey = UUID.randomUUID().toString();
            var accepted = new AtomicReference<Map<String, Object>>();
            String invalid = body(code).replace("ALPHANUMERIC_V1", "UNSUPPORTED_V1").replace("\"name\":", "\"minimumLength\":32768,\"name\":");
            asserter.assertThat(() -> race(winner, () -> post(winner, body(code), winningKey), () -> post(loser, invalid, losingKey),
                    () -> control.awaitCodeWait(code)), responses -> {
                accepted.set(success(responses.getFirst(), 201, winner));
                error(responses.getLast(), 409, "identifier-scheme-code-conflict", loser);
            });
            asserter.assertThat(() -> completion(loser, "create", losingKey), records -> assertTrue(records.isEmpty()));
            for (String action : List.of("activate", "deprecate", "retire")) {
                asserter.execute(() -> lifecycle(winner, accepted.get(), action, null, version(accepted.get()))
                        .invoke(response -> accepted.set(success(response, 200, winner))));
                asserter.assertThat(() -> post(loser, invalid, losingKey), response -> error(response, 409, "identifier-scheme-code-conflict", loser));
                asserter.assertThat(() -> get(loser, accepted.get()), response -> assertEquals(accepted.get(), success(response, 200, loser)));
                asserter.assertThat(() -> completion(loser, "create", losingKey), records -> assertTrue(records.isEmpty()));
            }
        }
    }

    @Test @RunOnVertxContext
    void everyStateActionSetupArbitratesWithPatchWithoutPartialFieldsOrExtraAdvance(UniAsserter asserter) {
        for (String state : List.of("DRAFT", "ACTIVE", "DEPRECATED", "RETIRED")) {
            for (String action : List.of("activate", "deprecate", "retire")) {
                arbitrateStateActionWithPatch(asserter, state, action);
            }
        }
        for (String action : List.of("activate", "deprecate", "retire")) {
            arbitrateLifecycleWinnerOverPatch(asserter, action);
        }
    }

    private void arbitrateStateActionWithPatch(UniAsserter asserter, String state, String action) {
        var creator = Context.fresh();
        var patcher = actor(creator, "patch-winner");
        var actor = actor(creator, "action-contender");
        var source = new AtomicReference<Map<String, Object>>();
        var accepted = new AtomicReference<Map<String, Object>>();
        asserter.execute(() -> prepare(creator, state).invoke(source::set));
        if (state.equals("RETIRED")) {
            asserter.assertThat(() -> Uni.combine().all().unis(patch(patcher, source.get(), "{\"name\":\"Rejected\"}"),
                    lifecycle(actor, source.get(), action, null, version(source.get()))).asTuple(), responses -> {
                error(responses.getItem1(), 409, "identifier-scheme-retired", patcher);
                error(responses.getItem2(), 409, "invalid-identifier-scheme-lifecycle", actor);
            });
            asserter.assertThat(() -> get(creator, source.get()), response -> assertEquals(source.get(), success(response, 200, creator)));
            asserter.assertThat(() -> row(source.get()), stored -> assertRow(source.get(), stored, creator.user(), creator.user()));
            return;
        }
        String changes = state.equals("DRAFT")
                ? "{\"name\":\"Winning Name\",\"description\":null,\"normalizerKey\":\"TRIM_UPPERCASE_V1\",\"validatorKey\":\"EC_TAX_ID_V1\",\"minimumLength\":13,\"maximumLength\":13,\"requiresExpiration\":true}"
                : "{\"name\":\"Winning Name\",\"description\":null}";
        asserter.assertThat(() -> race(patcher, () -> patch(patcher, source.get(), changes),
                () -> lifecycle(actor, source.get(), action, "independent-action", version(source.get())),
                () -> control.awaitRowWait(activeProbe)), responses -> {
            accepted.set(success(responses.getFirst(), 200, patcher));
            error(responses.getLast(), 412, "expected-version-mismatch", actor);
            var expected = new HashMap<>(source.get());
            expected.put("name", "Winning Name");
            expected.remove("description");
            expected.put("version", (int) version(source.get()) + 1);
            expected.put("updatedAt", accepted.get().get("updatedAt"));
            if (state.equals("DRAFT")) {
                expected.put("validatorKey", "EC_TAX_ID_V1");
                expected.put("minimumLength", 13);
                expected.put("maximumLength", 13);
                expected.put("requiresExpiration", true);
            }
            assertEquals(expected, accepted.get());
            assertTrue(Instant.parse((String) accepted.get().get("updatedAt")).isAfter(Instant.parse((String) source.get().get("updatedAt"))));
        });
        asserter.assertThat(() -> row(source.get()), stored -> assertRow(accepted.get(), stored, creator.user(), "patch-winner"));
        asserter.assertThat(() -> get(creator, source.get()), response -> assertEquals(accepted.get(), success(response, 200, creator)));
        asserter.assertThat(() -> completion(actor, action, "independent-action"), records -> assertTrue(records.isEmpty()));
    }

    private void arbitrateLifecycleWinnerOverPatch(UniAsserter asserter, String action) {
        var creator = Context.fresh();
        var winner = actor(creator, "action-winner");
        var loser = actor(creator, "patch-loser");
        var source = new AtomicReference<Map<String, Object>>();
        var accepted = new AtomicReference<Map<String, Object>>();
        asserter.execute(() -> prepare(creator, action.equals("deprecate") ? "ACTIVE" : "DRAFT").invoke(source::set));
        asserter.assertThat(() -> race(winner, () -> lifecycle(winner, source.get(), action, "winning-action", version(source.get())),
                () -> patch(loser, source.get(), "{\"name\":\"Losing Name\",\"description\":null}"), () -> control.awaitRowWait(activeProbe)), responses -> {
            accepted.set(success(responses.getFirst(), 200, winner));
            error(responses.getLast(), 412, "expected-version-mismatch", loser);
            var expected = new HashMap<>(source.get());
            expected.put("version", (int) version(source.get()) + 1);
            expected.put("status", target(action));
            expected.put("updatedAt", accepted.get().get("updatedAt"));
            assertEquals(expected, accepted.get());
        });
        asserter.assertThat(() -> row(source.get()), stored -> assertRow(accepted.get(), stored, creator.user(), "action-winner"));
        asserter.assertThat(() -> get(creator, source.get()), response -> assertEquals(accepted.get(), success(response, 200, creator)));
        asserter.assertThat(() -> completion(winner, action, "winning-action"), stored -> assertSnapshot(accepted.get(), stored, creator.user(), "action-winner"));
        asserter.assertThat(() -> lifecycle(loser, source.get(), action, "winning-action", version(source.get())),
                response -> assertEquals(accepted.get(), success(response, 200, loser)));
    }

    @Test @RunOnVertxContext
    void independentPatchContendersCannotMergeOrAdvanceTheWinningRevisionAgain(UniAsserter asserter) {
        var creator = Context.fresh(); var winner = actor(creator, "patch-winner"); var loser = actor(creator, "patch-loser");
        var source = new AtomicReference<Map<String, Object>>(); var accepted = new AtomicReference<Map<String, Object>>();
        asserter.execute(() -> prepare(creator, "DRAFT").invoke(source::set));
        asserter.assertThat(() -> race(winner, () -> patch(winner, source.get(), "{\"name\":\"Winning Name\",\"description\":null,\"minimumLength\":5}"),
                () -> patch(loser, source.get(), "{\"name\":\"Losing Name\",\"validatorKey\":\"EC_NATIONAL_ID_V1\",\"maximumLength\":10}"),
                () -> control.awaitRowWait(activeProbe)), responses -> {
            accepted.set(success(responses.getFirst(), 200, winner)); error(responses.getLast(), 412, "expected-version-mismatch", loser);
            var expected = new HashMap<>(source.get()); expected.put("name", "Winning Name"); expected.remove("description");
            expected.put("minimumLength", 5); expected.put("version", 1); expected.put("updatedAt", accepted.get().get("updatedAt"));
            assertEquals(expected, accepted.get());
        });
        asserter.assertThat(() -> row(source.get()), stored -> assertRow(accepted.get(), stored, creator.user(), winner.user()));
        asserter.assertThat(() -> counts(creator, (String) source.get().get("code")), actual -> assertEquals(List.of(1L, 1L, 0L), actual));
    }

    @Test @RunOnVertxContext
    void realCompletionAndPreacceptanceFailuresRollBackAndFailedKeysAreReusable(UniAsserter asserter) {
        for (Mode fault : List.of(Mode.FAIL_RECORD, Mode.FAIL_ACCEPTANCE)) {
            var context = Context.fresh(); String code = uniqueCode(); String key = UUID.randomUUID().toString();
            var probe = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
            asserter.execute(() -> probe.set(control.arm(UUID.fromString(context.process()), fault)));
            asserter.assertThat(() -> post(context, body(code), key), response -> {
                error(response, 500, "server-error", context); assertTrue(probe.get().closed());
            });
            asserter.assertThat(() -> counts(context, code), actual -> assertEquals(List.of(0L, 0L, 0L), actual));
            asserter.execute(control::clear);
            asserter.execute(() -> post(context, body(code), key).invoke(response -> success(response, 201, context)));
            for (String action : List.of("activate", "deprecate", "retire")) {
                var source = new AtomicReference<Map<String, Object>>();
                String actionKey = UUID.randomUUID().toString();
                asserter.execute(() -> prepare(context, action.equals("deprecate") ? "ACTIVE" : "DRAFT").invoke(source::set));
                asserter.execute(() -> probe.set(control.arm(UUID.fromString(context.process()), fault)));
                asserter.assertThat(() -> lifecycle(context, source.get(), action, actionKey, version(source.get())), response -> {
                    error(response, 500, "server-error", context); assertTrue(probe.get().closed());
                });
                asserter.assertThat(() -> get(context, source.get()), response -> assertEquals(source.get(), success(response, 200, context)));
                asserter.assertThat(() -> completion(context, action, actionKey), records -> assertTrue(records.isEmpty()));
                asserter.execute(control::clear);
                asserter.assertThat(() -> lifecycle(context, source.get(), action, actionKey, version(source.get())), response -> success(response, 200, context));
            }
            asserter.assertThat(() -> outbox(context), events -> assertEquals(0L, events));
        }
    }

    @Test @RunOnVertxContext
    void actualOperationAndQueryTimeoutsReturn503OnlyAfterResourceCleanup(UniAsserter asserter) {
        var context = Context.fresh(); String code = uniqueCode(); String key = UUID.randomUUID().toString();
        var probe = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
        asserter.execute(() -> probe.set(control.arm(UUID.fromString(context.process()), Mode.OPERATION_TIMEOUT)));
        asserter.assertThat(() -> post(context, body(code), key), response -> {
            error(response, 503, "dependency-unavailable", context); assertTrue(probe.get().closed());
        });
        asserter.assertThat(() -> counts(context, code), actual -> assertEquals(List.of(0L, 0L, 0L), actual));
        asserter.execute(control::clear);
        var source = new AtomicReference<Map<String, Object>>();
        asserter.execute(() -> post(context, body(code), key).invoke(response -> source.set(success(response, 201, context))));
        for (Mode mode : List.of(Mode.QUERY_STATEMENT_TIMEOUT, Mode.QUERY_TRAVERSAL_TIMEOUT)) {
            asserter.execute(() -> {
                probe.set(control.arm(UUID.randomUUID(), mode));
                control.slowRead(UUID.fromString((String) source.get().get("id")), probe.get());
            });
            asserter.assertThat(() -> get(context, source.get()), response -> {
                error(response, 503, "dependency-unavailable", context); assertTrue(probe.get().closed());
            });
            asserter.execute(control::clear);
            asserter.assertThat(() -> get(context, source.get()), response -> assertEquals(source.get(), success(response, 200, context)));
        }
    }

    @Test @RunOnVertxContext
    void lifecycleTimeoutAndDownstreamCancellationReleaseAllPendingChangesAndCompletion(UniAsserter asserter) {
        for (String action : List.of("activate", "deprecate", "retire")) {
            var context = Context.fresh(); String key = UUID.randomUUID().toString();
            var source = new AtomicReference<Map<String, Object>>(); var original = new AtomicReference<String>();
            var probe = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
            asserter.execute(() -> prepare(context, action.equals("deprecate") ? "ACTIVE" : "DRAFT").invoke(source::set));
            asserter.execute(() -> fullScheme(source.get()).invoke(original::set));
            asserter.execute(() -> probe.set(control.arm(UUID.fromString(context.process()), Mode.OPERATION_TIMEOUT)));
            asserter.assertThat(() -> lifecycle(context, source.get(), action, key, version(source.get())), response -> {
                error(response, 503, "dependency-unavailable", context); assertTrue(probe.get().closed());
            });
            asserter.assertThat(() -> fullScheme(source.get()), actual -> assertEquals(original.get(), actual));
            asserter.assertThat(() -> completion(context, action, key), records -> assertTrue(records.isEmpty()));
            asserter.execute(control::clear);
            asserter.execute(() -> {
                probe.set(control.arm(UUID.fromString(context.process()), Mode.HOLD));
                var dropped = new CopyOnWriteArrayList<Throwable>();
                Infrastructure.setDroppedExceptionHandler(dropped::add);
                var command = new ChangeIdentifierSchemeLifecycleCommand(new RequestMetadata(new TenantId(UUID.fromString(context.tenant())),
                        context.user(), UUID.fromString(context.process())), new IdentifierSchemeId(UUID.fromString((String) source.get().get("id"))),
                        new IdentifierSchemeVersion(version(source.get())), IdentifierSchemeLifecycleAction.valueOf(action.toUpperCase(java.util.Locale.ROOT)), Optional.of(key));
                var subscriber = lifecycleUseCase.execute(command).subscribe().withSubscriber(UniAssertSubscriber.create());
                return probe.get().reached().invoke(subscriber::cancel).chain(() -> Multi.createBy().repeating()
                        .uni(() -> Uni.createFrom().item(probe.get()::closed).onItem().delayIt().by(Duration.ofMillis(5)))
                        .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(Duration.ofSeconds(4)).fail())
                        .invoke(() -> { assertNull(subscriber.getItem()); assertNull(subscriber.getFailure()); assertTrue(dropped.isEmpty(), dropped.toString()); })
                        .eventually(subscriber::cancel).eventually(Infrastructure::resetDroppedExceptionHandler).eventually(control::clear);
            });
            asserter.assertThat(() -> fullScheme(source.get()), actual -> assertEquals(original.get(), actual));
            asserter.assertThat(() -> completion(context, action, key), records -> assertTrue(records.isEmpty()));
            asserter.assertThat(() -> lifecycle(context, source.get(), action, key, version(source.get())), response -> success(response, 200, context));
            asserter.assertThat(() -> outbox(context), events -> assertEquals(0L, events));
        }
    }

    @Test @RunOnVertxContext
    void genuineContenderLockTimeoutReturns503AfterRollbackAndThenReusesReleasedResources(UniAsserter asserter) {
        var winner = Context.fresh(); var loser = actor(winner, "lock-timeout-contender");
        String code = uniqueCode(); String key = UUID.randomUUID().toString();
        var holder = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
        var contender = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
        var accepted = new AtomicReference<Map<String, Object>>();
        asserter.assertThat(() -> {
            holder.set(control.arm(UUID.fromString(winner.process()), Mode.HOLD));
            contender.set(control.arm(UUID.fromString(loser.process()), Mode.LOCK_TIMEOUT));
            var losing = holder.get().reached().chain(() -> post(loser, body(code), key)).invoke(response -> {
                error(response, 503, "dependency-unavailable", loser); assertTrue(contender.get().closed());
            }).call(() -> counts(loser, code).invoke(actual -> assertEquals(List.of(0L, 0L, 0L), actual)))
                    .invoke(holder.get()::release);
            var observedWait = holder.get().reached().chain(() -> control.awaitKeyWait(winner.tenant(), "identifier-scheme.create.v1", key));
            return Uni.combine().all().unis(post(winner, body(code), key), losing, observedWait).asTuple()
                    .eventually(holder.get()::release).eventually(control::clear);
        }, responses -> accepted.set(success(responses.getItem1(), 201, winner)));
        asserter.assertThat(() -> post(loser, body(code), key), response -> assertEquals(accepted.get(), success(response, 201, loser)));
        asserter.assertThat(() -> counts(winner, code), actual -> assertEquals(List.of(1L, 1L, 0L), actual));
    }

    @Test @RunOnVertxContext
    void disconnectedHttpAfterCommitRetainsCreationAndLifecycleUnlikeRollback(UniAsserter asserter) {
        var context = Context.fresh(); var retry = actor(context, "lost-response-retry");
        String code = uniqueCode(); String key = UUID.randomUUID().toString();
        var probe = new AtomicReference<IdentifierSchemeCoordinatedPort.Probe>();
        var source = new AtomicReference<Map<String, Object>>();
        asserter.execute(() -> probe.set(control.arm(UUID.fromString(context.process()), Mode.LOSE_AFTER_COMMIT)));
        asserter.assertFailedWith(() -> IdentifierSchemeReactiveHttp.send(vertx, server, context, "POST", ROOT, body(code),
                Map.of("Idempotency-Key", key), probe.get().reached()), failure -> {
            assertFalse(failure instanceof io.smallrye.mutiny.TimeoutException); assertTrue(probe.get().closed());
        });
        asserter.execute(control::clear);
        asserter.assertThat(() -> counts(context, code), actual -> assertEquals(List.of(1L, 1L, 0L), actual));
        asserter.execute(() -> post(retry, body(code), key).invoke(response -> source.set(success(response, 201, retry))));
        asserter.assertThat(() -> completion(context, "create", key), records -> assertSnapshot(source.get(), records, context.user(), context.user()));
        asserter.execute(() -> probe.set(control.arm(UUID.fromString(context.process()), Mode.LOSE_AFTER_COMMIT)));
        asserter.assertFailedWith(() -> IdentifierSchemeReactiveHttp.send(vertx, server, context, "POST", ROOT + "/" + source.get().get("id") + "/activate", null,
                Map.of("Idempotency-Key", key, "If-Match", "0"), probe.get().reached()), failure -> {
            assertFalse(failure instanceof io.smallrye.mutiny.TimeoutException); assertTrue(probe.get().closed());
        });
        asserter.execute(control::clear);
        var accepted = new AtomicReference<Map<String, Object>>();
        asserter.execute(() -> lifecycle(retry, source.get(), "activate", key, 0).invoke(response -> accepted.set(success(response, 200, retry))));
        asserter.assertThat(() -> completion(context, "activate", key), records -> assertSnapshot(accepted.get(), records, context.user(), context.user()));
        asserter.execute(() -> lifecycle(retry, accepted.get(), "retire", null, 1).invoke(response -> success(response, 200, retry)));
        asserter.assertThat(() -> lifecycle(retry, source.get(), "activate", key, 0), response -> assertEquals(accepted.get(), success(response, 200, retry)));
        asserter.assertThat(() -> counts(context, code), actual -> assertEquals(List.of(1L, 2L, 0L), actual));
    }

    private IdentifierSchemeCoordinatedPort.Probe activeProbe;

    private Uni<List<Response>> race(Context holder, Supplier<Uni<Response>> first, Supplier<Uni<Response>> second, Supplier<Uni<Void>> waiting) {
        return Uni.createFrom().deferred(() -> {
            activeProbe = control.arm(UUID.fromString(holder.process()), Mode.HOLD);
            var probe = activeProbe;
            var contender = probe.reached().chain(second::get);
            var coordinator = probe.reached().chain(waiting::get).invoke(probe::release);
            return Uni.combine().all().unis(first.get(), contender, coordinator).asTuple()
                    .map(tuple -> List.of(tuple.getItem1(), tuple.getItem2()))
                    .ifNoItem().after(Duration.ofSeconds(12)).fail().eventually(probe::release).eventually(control::clear);
        });
    }

    private Uni<Map<String, Object>> prepare(Context context, String state) {
        return post(context, body(uniqueCode()).replace("\"name\":", "\"description\":\"Original description\",\"minimumLength\":2,\"maximumLength\":20,\"name\":"), UUID.randomUUID().toString())
                .map(response -> success(response, 201, context)).chain(created -> {
                    Uni<Map<String, Object>> sequence = Uni.createFrom().item(created);
                    for (String action : switch (state) {
                        case "ACTIVE" -> List.of("activate"); case "DEPRECATED" -> List.of("activate", "deprecate");
                        case "RETIRED" -> List.of("retire"); default -> List.<String>of();
                    }) sequence = sequence.chain(current -> lifecycle(context, current, action, null, version(current)).map(response -> success(response, 200, context)));
                    return sequence;
                });
    }

    private Uni<Response> post(Context context, String input, String key) {
        return send(context, "POST", ROOT, input, Map.of("Idempotency-Key", key));
    }
    private Uni<Response> get(Context context, Map<String, Object> scheme) { return send(context, "GET", ROOT + "/" + scheme.get("id"), null, Map.of()); }
    private Uni<Response> patch(Context context, Map<String, Object> scheme, String input) {
        return send(context, "PATCH", ROOT + "/" + scheme.get("id"), input, Map.of("If-Match", Long.toString(version(scheme))));
    }
    private Uni<Response> lifecycle(Context context, Map<String, Object> scheme, String action, String key, long version) {
        var headers = new HashMap<String, String>(); headers.put("If-Match", Long.toString(version));
        if (key != null) headers.put("Idempotency-Key", key);
        return send(context, "POST", ROOT + "/" + scheme.get("id") + "/" + action, null, headers);
    }
    private Uni<Response> send(Context context, String method, String path, String input, Map<String, String> headers) {
        return IdentifierSchemeReactiveHttp.send(vertx, server, context, method, path, input, headers);
    }
    private static Context actor(Context context, String user) { return new Context(context.tenant(), user, UUID.randomUUID().toString()); }
    private static long version(Map<String, Object> scheme) { return ((Number) scheme.get("version")).longValue(); }
    private static String target(String action) { return switch (action) { case "activate" -> "ACTIVE"; case "deprecate" -> "DEPRECATED"; default -> "RETIRED"; }; }

    private Uni<List<Long>> counts(Context context, String code) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select (select count(*) from identifier_schemes where code = :code),
                       (select count(*) from identifier_scheme_idempotency_records where tenant_id = :tenant),
                       (select count(*) from party_outbox_events where tenant_id = :tenant)
                """, Object[].class).setParameter("code", code).setParameter("tenant", UUID.fromString(context.tenant())).getSingleResult()
                .map(values -> List.of(((Number) values[0]).longValue(), ((Number) values[1]).longValue(), ((Number) values[2]).longValue())));
    }
    private Uni<Long> outbox(Context context) {
        return sessions.withSession(session -> session.createNativeQuery("select count(*) from party_outbox_events where tenant_id = :tenant", Long.class)
                .setParameter("tenant", UUID.fromString(context.tenant())).getSingleResult());
    }
    private Uni<List<String>> completion(Context context, String action, String key) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select result_snapshot::text from identifier_scheme_idempotency_records
                where tenant_id = :tenant and operation = :operation and idempotency_key = :key
                """, String.class).setParameter("tenant", UUID.fromString(context.tenant())).setParameter("operation", "identifier-scheme." + action + ".v1")
                .setParameter("key", key).getResultList());
    }
    private Uni<Object[]> row(Map<String, Object> source) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select name, description, normalizer_key, validator_key, minimum_length, maximum_length, requires_expiration,
                       status::text, version, created_at::text, updated_at::text, created_by, updated_by
                from identifier_schemes where id = :id
                """, Object[].class).setParameter("id", UUID.fromString((String) source.get("id"))).getSingleResult());
    }
    private Uni<String> fullScheme(Map<String, Object> source) {
        return sessions.withSession(session -> session.createNativeQuery("select to_jsonb(s)::text from identifier_schemes s where id = :id", String.class)
                .setParameter("id", UUID.fromString((String) source.get("id"))).getSingleResult());
    }
    private static void assertRow(Map<String, Object> expected, Object[] actual, String creator, String actor) {
        var fields = List.of("name", "description", "normalizerKey", "validatorKey", "minimumLength", "maximumLength", "requiresExpiration", "status", "version");
        for (int index = 0; index < fields.size(); index++) {
            Object value = actual[index]; if (value instanceof Number number) value = number.longValue();
            Object wanted = expected.get(fields.get(index)); if (wanted instanceof Number number) wanted = number.longValue();
            assertEquals(wanted, value, fields.get(index));
        }
        assertEquals(Instant.parse((String) expected.get("createdAt")), java.time.OffsetDateTime.parse(actual[9].toString().replace(' ', 'T')).toInstant());
        assertEquals(Instant.parse((String) expected.get("updatedAt")), java.time.OffsetDateTime.parse(actual[10].toString().replace(' ', 'T')).toInstant());
        assertEquals(creator, actual[11]); assertEquals(actor, actual[12]);
    }
    private static void assertSnapshot(Map<String, Object> expected, List<String> records, String creator, String actor) {
        assertEquals(1, records.size());
        var scheme = new io.vertx.core.json.JsonObject(records.getFirst()).getJsonObject("scheme");
        assertEquals(creator, scheme.remove("createdBy")); assertEquals(actor, scheme.remove("updatedBy"));
        for (String optional : List.of("description", "minimumLength", "maximumLength")) if (scheme.getValue(optional) == null) scheme.remove(optional);
        assertEquals(expected.keySet(), scheme.fieldNames());
        expected.forEach((field, value) -> {
            Object actual = scheme.getValue(field);
            if (value instanceof Number number) assertEquals(number.longValue(), ((Number) actual).longValue(), field);
            else assertEquals(value, actual, field);
        });
    }
}
