package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationOutcome;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentNaturalPersonCreationPort;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Provides deterministic fixtures and output-port doubles for application use
 * case tests.
 */
final class UseCaseTestSupport {

    static final Instant NOW = Instant.parse("2026-08-30T10:15:30Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, Month.AUGUST, 30);
    static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("0198ce2c-609c-7c04-a977-425e7d60c58d"));
    static final RequestMetadata METADATA = new RequestMetadata(
            TENANT_ID,
            "application-test-user",
            UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private UseCaseTestSupport() {
    }

    static NaturalPerson naturalPerson(
            PartyVersion version,
            String preferredName,
            LocalDate birthDate,
            LocalDate dateOfDeath,
            String birthCountryCode) {
        return NaturalPerson.restore(
                PARTY_ID,
                TENANT_ID,
                "Ada Lovelace",
                PartyRecordStatus.DRAFT,
                version,
                AuditInfo.initial(NOW.minusSeconds(60), "creator"),
                new NaturalPersonDetails(
                        "Ada",
                        "Lovelace",
                        preferredName,
                        birthDate,
                        dateOfDeath,
                        birthCountryCode));
    }

    static NaturalPerson persisted(NaturalPerson updated) {
        return NaturalPerson.restore(
                updated.partyId(),
                updated.tenantId(),
                updated.displayName(),
                updated.recordStatus(),
                updated.version().next(),
                updated.auditInfo(),
                updated.details());
    }

    static <T> T awaitItem(Uni<T> uni) {
        UniAssertSubscriber<T> subscriber = uni.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitItem(TIMEOUT).assertCompleted();
        return subscriber.getItem();
    }

    static ApplicationFailure awaitFailure(Uni<?> uni) {
        Throwable failure = awaitThrowable(uni);
        return assertInstanceOf(ApplicationException.class, failure).failure();
    }

    static Throwable awaitThrowable(Uni<?> uni) {
        UniAssertSubscriber<?> subscriber = uni.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitFailure(TIMEOUT).assertFailed();
        return subscriber.getFailure();
    }

    /**
     * Records repository calls and returns configurable reactive outcomes.
     */
    static final class RepositoryDouble implements NaturalPersonRepository {

        Optional<NaturalPerson> found = Optional.empty();
        Function<FindCall, Uni<Optional<NaturalPerson>>> findBehavior = call -> Uni.createFrom().item(found);
        Function<UpdateCall, Uni<NaturalPerson>> updateBehavior = call -> Uni.createFrom()
                .item(persisted(call.updatedPerson()));
        final List<FindCall> findCalls = new ArrayList<>();
        final List<UpdateCall> updateCalls = new ArrayList<>();

        @Override
        public Uni<Optional<NaturalPerson>> findByTenantAndId(TenantId tenantId, PartyId partyId) {
            FindCall call = new FindCall(tenantId, partyId);
            findCalls.add(call);
            return findBehavior.apply(call);
        }

        @Override
        public Uni<NaturalPerson> update(NaturalPerson updatedPerson, PartyVersion expectedVersion) {
            UpdateCall call = new UpdateCall(updatedPerson, expectedVersion);
            updateCalls.add(call);
            return updateBehavior.apply(call);
        }
    }

    /**
     * Records country-reference calls and returns a configurable outcome.
     */
    static final class CountryReferenceDouble implements CountryReferencePort {

        BiFunction<RequestMetadata, String, Uni<Boolean>> behavior = (metadata, code) -> Uni.createFrom()
                .item(true);
        final List<CountryCall> calls = new ArrayList<>();
        final List<String> codes = new ArrayList<>();

        @Override
        public Uni<Boolean> isRecognizedCountry(
                RequestMetadata requestMetadata,
                String alpha2Code) {
            calls.add(new CountryCall(requestMetadata, alpha2Code));
            codes.add(alpha2Code);
            return behavior.apply(requestMetadata, alpha2Code);
        }
    }

    /**
     * Records idempotent creation calls and returns a configurable outcome.
     */
    static final class CreationPortDouble implements IdempotentNaturalPersonCreationPort {

        Function<CreationCall, Uni<IdempotentCreationResult>> behavior = call -> Uni.createFrom()
                .item(new IdempotentCreationResult(
                        NaturalPersonResult.fromAggregate(call.naturalPerson()),
                        IdempotentCreationOutcome.CREATED));
        Function<PreflightCall, Uni<Optional<IdempotentCreationResult>>> preflightBehavior = call -> Uni
                .createFrom().item(Optional.empty());
        final List<PreflightCall> preflightCalls = new ArrayList<>();
        final List<CreationCall> calls = new ArrayList<>();

        @Override
        public Uni<Optional<IdempotentCreationResult>> findCompleted(
                TenantId tenantId,
                String idempotencyKey,
                String requestHash) {
            PreflightCall call = new PreflightCall(tenantId, idempotencyKey, requestHash);
            preflightCalls.add(call);
            return preflightBehavior.apply(call);
        }

        @Override
        public Uni<IdempotentCreationResult> createIdempotently(
                TenantId tenantId,
                String idempotencyKey,
                String requestHash,
                NaturalPerson naturalPerson) {
            CreationCall call = new CreationCall(
                    tenantId,
                    idempotencyKey,
                    requestHash,
                    naturalPerson);
            calls.add(call);
            return behavior.apply(call);
        }
    }

    /** Records one repository lookup. */
    record FindCall(TenantId tenantId, PartyId partyId) {
    }

    /** Records one optimistic repository update. */
    record UpdateCall(NaturalPerson updatedPerson, PartyVersion expectedVersion) {
    }

    /** Records one country-reference validation. */
    record CountryCall(RequestMetadata requestMetadata, String alpha2Code) {
    }

    /** Records one completed-result idempotency lookup. */
    record PreflightCall(TenantId tenantId, String idempotencyKey, String requestHash) {
    }

    /** Records one atomic idempotent creation attempt. */
    record CreationCall(
            TenantId tenantId,
            String idempotencyKey,
            String requestHash,
            NaturalPerson naturalPerson) {
    }
}
