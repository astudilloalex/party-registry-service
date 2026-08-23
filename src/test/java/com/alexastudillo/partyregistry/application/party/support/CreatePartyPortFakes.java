package com.alexastudillo.partyregistry.application.party.support;

import com.alexastudillo.partyregistry.application.party.command.PartyCreationEventIntent;
import com.alexastudillo.partyregistry.application.party.port.out.ActiveCountryReferenceValidationPort;
import com.alexastudillo.partyregistry.application.party.port.out.CreatePartyPersistencePort;
import com.alexastudillo.partyregistry.application.party.port.out.PartyEventPolicy;
import com.alexastudillo.partyregistry.application.party.port.out.TimeProvider;
import com.alexastudillo.partyregistry.application.party.result.CountryValidationOutcome;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;
import com.alexastudillo.partyregistry.domain.party.model.CountryCode;
import com.alexastudillo.partyregistry.domain.party.model.Party;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CreatePartyPortFakes {

    private CreatePartyPortFakes() {
    }

    public static final class CountryValidationPortFake
            implements ActiveCountryReferenceValidationPort {

        private final List<CountryValidationCall> calls = new CopyOnWriteArrayList<>();
        private volatile Uni<CountryValidationOutcome> response =
                Uni.createFrom().item(new CountryValidationOutcome.AllActive());

        @Override
        public Uni<CountryValidationOutcome> validateAll(
                Set<CountryCode> activeCountryCodes,
                String tenantHeaderValue,
                String auditSubject,
                String processHeaderValue) {
            calls.add(new CountryValidationCall(
                    Set.copyOf(activeCountryCodes), tenantHeaderValue, auditSubject, processHeaderValue));
            return response;
        }

        public void respondWith(CountryValidationOutcome outcome) {
            response = Uni.createFrom().item(outcome);
        }

        public void failWith(Throwable failure) {
            response = Uni.createFrom().failure(failure);
        }

        public List<CountryValidationCall> calls() {
            return List.copyOf(calls);
        }
    }

    public static final class PartyPersistencePortFake implements CreatePartyPersistencePort {

        private final List<PersistenceCall> calls = new CopyOnWriteArrayList<>();
        private final Queue<UniEmitter<? super CreatePartyResult>> pendingCommits =
                new ConcurrentLinkedQueue<>();

        @Override
        public Uni<CreatePartyResult> persist(
                Party party,
                Optional<PartyCreationEventIntent> eventIntent) {
            calls.add(new PersistenceCall(party, eventIntent));
            return Uni.createFrom().<CreatePartyResult>emitter(pendingCommits::add);
        }

        public void commit(CreatePartyResult result) {
            nextPendingCommit().complete(result);
        }

        public void failCommit(Throwable failure) {
            nextPendingCommit().fail(failure);
        }

        public int pendingCommitCount() {
            return pendingCommits.size();
        }

        public List<PersistenceCall> calls() {
            return List.copyOf(calls);
        }

        private UniEmitter<? super CreatePartyResult> nextPendingCommit() {
            var emitter = pendingCommits.poll();
            if (emitter == null) {
                throw new IllegalStateException("No subscribed persistence call is awaiting commit");
            }
            return emitter;
        }
    }

    public static final class PartyEventPolicyFake implements PartyEventPolicy {

        private final List<PartyEventPolicyCall> calls = new CopyOnWriteArrayList<>();
        private volatile Uni<Boolean> response = Uni.createFrom().item(false);

        @Override
        public Uni<Boolean> shouldRecord(String eventType) {
            calls.add(new PartyEventPolicyCall(eventType));
            return response;
        }

        public void respondWith(boolean shouldRecord) {
            response = Uni.createFrom().item(shouldRecord);
        }

        public void failWith(Throwable failure) {
            response = Uni.createFrom().failure(failure);
        }

        public List<PartyEventPolicyCall> calls() {
            return List.copyOf(calls);
        }
    }

    public static final class TimeProviderFake implements TimeProvider {

        private final List<TimeProviderCall> calls = new CopyOnWriteArrayList<>();
        private volatile Uni<Instant> response;

        public TimeProviderFake(Instant currentTime) {
            respondWith(currentTime);
        }

        @Override
        public Uni<Instant> now() {
            calls.add(new TimeProviderCall());
            return response;
        }

        public void respondWith(Instant currentTime) {
            response = Uni.createFrom().item(currentTime);
        }

        public void failWith(Throwable failure) {
            response = Uni.createFrom().failure(failure);
        }

        public List<TimeProviderCall> calls() {
            return List.copyOf(calls);
        }
    }

    public record CountryValidationCall(
            Set<CountryCode> activeCountryCodes,
            String tenantHeaderValue,
            String auditSubject,
            String processHeaderValue) {
    }

    public record PersistenceCall(
            Party party,
            Optional<PartyCreationEventIntent> eventIntent) {
    }

    public record PartyEventPolicyCall(String eventType) {
    }

    public record TimeProviderCall() {
    }
}
