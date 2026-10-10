package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins safe identifier I/O signatures and exercises callback composition without exposing sessions or transport contracts. */
class PartyIdentifierPortContractTest {

    @Test
    void queryCursorAndFingerprintPortsUseOnlyQualifiedTypedContracts() throws ReflectiveOperationException {
        var detail = PartyIdentifierQueryPort.class.getMethod("findById", RequestMetadata.class, PartyId.class, PartyIdentifierId.class);
        assertTrue(detail.getGenericReturnType().getTypeName().contains(PartyIdentifierResult.class.getName()));
        assertTrue(detail.getGenericReturnType().getTypeName().contains(Optional.class.getName()));
        var page = PartyIdentifierQueryPort.class.getMethod("findPage", RequestMetadata.class, PartyIdentifierSearchScope.class,
                com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary.class);
        assertTrue(page.getGenericReturnType().getTypeName().contains("PartyIdentifierPageSlice"));
        assertEquals(String.class, PartyIdentifierCursorPort.class.getMethod("encode", PartyIdentifierSearchScope.class,
                com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary.class).getReturnType());
        assertEquals(String.class, PartyIdentifierFingerprintPort.class.getMethod("fingerprint", TenantId.class,
                com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest.class).getReturnType());
        for (Class<?> port : List.of(PartyIdentifierQueryPort.class, PartyIdentifierCursorPort.class,
                PartyIdentifierFingerprintPort.class, PartyIdentifierMutationPort.class, PartyIdentifierMutationContext.class)) {
            for (var method : port.getMethods()) {
                String signature = method.toGenericString();
                assertFalse(signature.contains("org.hibernate"));
                assertFalse(signature.contains("jakarta.ws.rs"));
                assertFalse(signature.contains("ApiResponse"));
            }
        }
    }

    @Test
    void completedResultsRetainOriginalAttributionAndHideRequestDigests() {
        var pending = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(
                PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, null));
        String digest = "a".repeat(64);
        var completion = new CompletedPartyIdentifierMutation(PartyIdentifierFixtures.TENANT, PartyIdentifierOperation.CREATE,
                digest, pending, null, PartyIdentifierFixtures.CREATOR, PartyIdentifierFixtures.CREATED_AT);
        assertEquals(pending, completion.result());
        assertEquals(PartyIdentifierFixtures.CREATED_AT, completion.acceptedAt());
        assertFalse(completion.toString().contains(digest));
        var expected = new PartyIdentifierVersion(3);
        assertThrows(IllegalArgumentException.class, () -> new CompletedPartyIdentifierMutation(
                PartyIdentifierFixtures.TENANT, PartyIdentifierOperation.CREATE, digest, pending, expected,
                PartyIdentifierFixtures.CREATOR, PartyIdentifierFixtures.CREATED_AT));
    }

    @Test
    void privateAuditUsesAcceptedContextAndAllowsOnlyWithdrawalActions() {
        var current = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, null);
        var metadata = new RequestMetadata(current.tenantId(), PartyIdentifierFixtures.ACTOR, UUID.randomUUID());
        var reason = new PartyIdentifierReason("", "private\0detail");
        var audit = new PartyIdentifierActionAudit(metadata, current.partyId(), current.identifierId(),
                new PartyIdentifierVersion(4), PartyIdentifierOperation.REVOKE, PartyIdentifierFixtures.OCCURRED_AT, reason);
        assertEquals(metadata, audit.requestMetadata());
        assertEquals(reason, audit.reason());
        assertFalse(audit.toString().contains("private\0detail"));
        var party = current.partyId();
        var identifier = current.identifierId();
        var version = audit.resultingVersion();
        var instant = audit.occurredAt();
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierActionAudit(
                metadata, party, identifier, version, PartyIdentifierOperation.VERIFY, instant, reason));
    }

    @Test
    void mutationCallbackReceivesOneTenantAndReturnsOnlyASafeCommittedOutcome() {
        var result = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(
                PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, null));
        var outcome = new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.REPLAYED);
        var metadata = new RequestMetadata(PartyIdentifierFixtures.TENANT, PartyIdentifierFixtures.ACTOR, UUID.randomUUID());
        List<String> calls = new ArrayList<>();
        PartyIdentifierMutationPort port = (contextMetadata, work) -> {
            PartyIdentifierMutationContext context = (PartyIdentifierMutationContext) java.lang.reflect.Proxy.newProxyInstance(
                    PartyIdentifierMutationContext.class.getClassLoader(), new Class<?>[]{PartyIdentifierMutationContext.class},
                    (proxy, method, arguments) -> {
                        calls.add(method.getName());
                        if (method.getName().equals("tenantId")) return contextMetadata.tenantId();
                        return Uni.createFrom().voidItem();
                    });
            return work.apply(context).invoke(() -> calls.add("committed"));
        };
        port.execute(metadata, context -> {
            assertEquals(metadata.tenantId(), context.tenantId());
            return context.serializeReplayKey(PartyIdentifierOperation.VERIFY, "exact-key")
                    .replaceWith(outcome);
        }).subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).assertItem(outcome).assertCompleted();
        assertEquals(List.of("tenantId", "serializeReplayKey", "committed"), calls);
        assertTrue(PartyIdentifierMutationPort.class.getMethods()[0].getGenericReturnType().getTypeName()
                .contains(PartyIdentifierMutationOutcome.class.getName()));
    }
}
