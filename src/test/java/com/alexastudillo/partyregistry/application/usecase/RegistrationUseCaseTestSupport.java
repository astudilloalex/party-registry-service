package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.PartyRegistrationCommand;
import com.alexastudillo.partyregistry.application.model.IdentifierProtectionRequest;
import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentPartyRegistrationPort;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierRegistrationPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Provides deterministic identifier-registration fixtures and recording port doubles.
 */
final class RegistrationUseCaseTestSupport {

    static final Instant NOW = Instant.parse("2026-09-04T00:30:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 9, 4);
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.ofHours(-5));
    static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991a56-f000-7000-8000-000000000001"));
    static final RequestMetadata METADATA = new RequestMetadata(
            TENANT_ID,
            "registration-test-user",
            UUID.fromString("01991a56-f000-7000-8000-000000000002"));
    static final String FINGERPRINT = "a".repeat(64);
    static final String COMPLETE_VALUE = "  ab1234  ";
    static final String NORMALIZED_VALUE = "AB1234";

    private RegistrationUseCaseTestSupport() {
    }

    static InitialPartyIdentifierInput identifier(String schemeCode) {
        return new InitialPartyIdentifierInput(
                schemeCode,
                COMPLETE_VALUE,
                "AUTHORITY",
                TODAY.minusYears(1),
                TODAY.plusYears(1),
                true);
    }

    static IdentifierScheme scheme(
            IdentifierSchemeStatus status,
            IdentifierSubjectType subjectType) {
        return scheme(status, subjectType, 4, 16, false);
    }

    static IdentifierScheme scheme(
            IdentifierSchemeStatus status,
            IdentifierSubjectType subjectType,
            Integer minimumLength,
            Integer maximumLength,
            boolean requiresExpiration) {
        return scheme(
                status,
                subjectType,
                minimumLength,
                maximumLength,
                requiresExpiration,
                "TRIM_UPPERCASE_V1",
                "ALPHANUMERIC_V1");
    }

    static IdentifierScheme scheme(
            IdentifierSchemeStatus status,
            IdentifierSubjectType subjectType,
            Integer minimumLength,
            Integer maximumLength,
            boolean requiresExpiration,
            String normalizerKey,
            String validatorKey) {
        return new IdentifierScheme(
                new IdentifierSchemeId(UUID.fromString("01991a56-f000-7000-8000-000000000003")),
                "TEST-SCHEME",
                "GB",
                IdentifierCategory.NATIONAL_ID,
                subjectType,
                "Test identifier",
                null,
                normalizerKey,
                validatorKey,
                minimumLength,
                maximumLength,
                requiresExpiration,
                status,
                new IdentifierSchemeVersion(3),
                AuditInfo.initial(NOW.minusSeconds(60), "catalog"));
    }

    static ProtectedIdentifierValue protectedValue() {
        return new ProtectedIdentifierValue(
                "v1.protected",
                1,
                "b".repeat(64),
                "**1234",
                new IdentifierRuleVersion(1));
    }

    static PartyRegistrationResult result(
            NaturalPersonRegistrationCandidate candidate,
            PartyRegistrationOutcome outcome) {
        return new PartyRegistrationResult(
                PartyDetailsResult.fromAggregate(candidate.party()),
                PartyIdentifierResult.fromAggregate(
                        candidate.initialIdentifier(),
                        candidate.identifierScheme()),
                outcome);
    }

    static PartyRegistrationResult result(
            LegalEntityRegistrationCandidate candidate,
            PartyRegistrationOutcome outcome) {
        return new PartyRegistrationResult(
                PartyDetailsResult.fromAggregate(candidate.party()),
                PartyIdentifierResult.fromAggregate(
                        candidate.initialIdentifier(),
                        candidate.identifierScheme()),
                outcome);
    }

    /** Records fingerprint requests without retaining canonical sensitive data. */
    static final class FingerprintDouble implements RegistrationFingerprintPort {

        final List<String> order;
        final List<PartyRegistrationCommand> commands = new ArrayList<>();
        Function<PartyRegistrationCommand, String> behavior = ignored -> FINGERPRINT;

        FingerprintDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public String fingerprint(PartyRegistrationCommand command) {
            order.add("fingerprint");
            commands.add(command);
            return behavior.apply(command);
        }

        @Override
        public boolean matches(String expectedFingerprint, String actualFingerprint) {
            return expectedFingerprint.equals(actualFingerprint);
        }
    }

    /** Records country-reference validation requests. */
    static final class CountryDouble implements CountryReferencePort {

        final List<String> order;
        final List<CountryCall> calls = new ArrayList<>();
        BiFunction<RequestMetadata, String, Uni<Boolean>> behavior = (metadata, code) ->
            Uni.createFrom().item(true);

        CountryDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public Uni<Boolean> isRecognizedCountry(RequestMetadata requestMetadata, String alpha2Code) {
            order.add("country");
            calls.add(new CountryCall(requestMetadata, alpha2Code));
            return behavior.apply(requestMetadata, alpha2Code);
        }
    }

    /** Records identifier-scheme lookups. */
    static final class SchemeDouble implements IdentifierSchemeRepository {

        final List<String> order;
        final List<String> codes = new ArrayList<>();
        Function<String, Uni<Optional<IdentifierScheme>>> behavior = code -> Uni.createFrom().item(
                Optional.of(scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH)));

        SchemeDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public Uni<Optional<IdentifierScheme>> findByCode(String code) {
            order.add("scheme");
            codes.add(code);
            return behavior.apply(code);
        }
    }

    /** Records transient protection requests and returns opaque protected values. */
    static final class ProtectionDouble implements IdentifierProtectionPort {

        final List<String> order;
        final List<IdentifierProtectionRequest> requests = new ArrayList<>();
        Function<IdentifierProtectionRequest, ProtectedIdentifierValue> behavior = ignored -> protectedValue();

        ProtectionDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public ProtectedIdentifierValue protect(IdentifierProtectionRequest request) {
            order.add("protect");
            requests.add(request);
            return behavior.apply(request);
        }
    }

    /** Records current-operation lookup, legacy-key checks, and atomic registration calls. */
    static final class PartyRegistrationPortDouble implements IdempotentPartyRegistrationPort {

        final List<String> order;
        final List<CompletedCall> completedCalls = new ArrayList<>();
        final List<LegacyCall> legacyCalls = new ArrayList<>();
        final List<NaturalPersonRegistrationCandidate> naturalCandidates = new ArrayList<>();
        final List<LegalEntityRegistrationCandidate> legalCandidates = new ArrayList<>();
        Function<CompletedCall, Uni<Optional<PartyRegistrationResult>>> completedBehavior = ignored ->
            Uni.createFrom().item(Optional.empty());
        Function<LegacyCall, Uni<Boolean>> legacyBehavior = ignored -> Uni.createFrom().item(false);
        Function<NaturalPersonRegistrationCandidate, Uni<PartyRegistrationResult>> naturalBehavior = candidate ->
            Uni.createFrom().item(result(candidate, PartyRegistrationOutcome.CREATED));
        Function<LegalEntityRegistrationCandidate, Uni<PartyRegistrationResult>> legalBehavior = candidate ->
            Uni.createFrom().item(result(candidate, PartyRegistrationOutcome.CREATED));

        PartyRegistrationPortDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public Uni<Optional<PartyRegistrationResult>> findCompleted(
                TenantId tenantId,
                String operation,
                String idempotencyKey,
                String registrationFingerprint) {
            order.add("findCompleted");
            CompletedCall call = new CompletedCall(
                    tenantId,
                    operation,
                    idempotencyKey,
                    registrationFingerprint);
            completedCalls.add(call);
            return completedBehavior.apply(call);
        }

        @Override
        public Uni<Boolean> hasCompletedKey(TenantId tenantId, String operation, String idempotencyKey) {
            order.add("hasCompletedKey");
            LegacyCall call = new LegacyCall(tenantId, operation, idempotencyKey);
            legacyCalls.add(call);
            return legacyBehavior.apply(call);
        }

        @Override
        public Uni<PartyRegistrationResult> registerNaturalPerson(
                NaturalPersonRegistrationCandidate candidate) {
            order.add("registerNaturalPerson");
            naturalCandidates.add(candidate);
            return naturalBehavior.apply(candidate);
        }

        @Override
        public Uni<PartyRegistrationResult> registerLegalEntity(
                LegalEntityRegistrationCandidate candidate) {
            order.add("registerLegalEntity");
            legalCandidates.add(candidate);
            return legalBehavior.apply(candidate);
        }
    }

    /** Records tenant-qualified Party type lookups. */
    static final class PartyLookupDouble implements PartyLookupPort {

        final List<String> order;
        final List<PartyLookupCall> calls = new ArrayList<>();
        Function<PartyLookupCall, Uni<Optional<PartyType>>> behavior = ignored ->
            Uni.createFrom().item(Optional.of(PartyType.NATURAL_PERSON));

        PartyLookupDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public Uni<Optional<PartyType>> findType(TenantId tenantId, com.alexastudillo.partyregistry.domain.model.PartyId partyId) {
            order.add("partyLookup");
            PartyLookupCall call = new PartyLookupCall(tenantId, partyId);
            calls.add(call);
            return behavior.apply(call);
        }
    }

    /** Records independent additional-identifier persistence calls. */
    static final class IdentifierRegistrationPortDouble implements PartyIdentifierRegistrationPort {

        final List<String> order;
        final List<PartyIdentifierRegistrationCandidate> candidates = new ArrayList<>();
        Function<PartyIdentifierRegistrationCandidate, Uni<PartyIdentifierResult>> behavior = candidate ->
            Uni.createFrom().item(PartyIdentifierResult.fromAggregate(
                    candidate.identifier(),
                    candidate.identifierScheme()));

        IdentifierRegistrationPortDouble(List<String> order) {
            this.order = order;
        }

        @Override
        public Uni<PartyIdentifierResult> register(PartyIdentifierRegistrationCandidate candidate) {
            order.add("registerIdentifier");
            candidates.add(candidate);
            return behavior.apply(candidate);
        }
    }

    /** Records one country-reference call. */
    record CountryCall(RequestMetadata metadata, String code) {
    }

    /** Records one completed current-operation lookup. */
    record CompletedCall(
            TenantId tenantId,
            String operation,
            String idempotencyKey,
            String fingerprint) {
    }

    /** Records one completed legacy-operation lookup. */
    record LegacyCall(TenantId tenantId, String operation, String idempotencyKey) {
    }

    /** Records one tenant-concealed Party lookup. */
    record PartyLookupCall(
            TenantId tenantId,
            com.alexastudillo.partyregistry.domain.model.PartyId partyId) {
    }
}
