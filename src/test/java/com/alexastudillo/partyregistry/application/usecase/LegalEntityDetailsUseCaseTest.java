package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.PatchLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.ReplaceLegalEntityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.LegalEntityRepository;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.LegalEntityPatch;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legal-detail orchestration ordering, typed failures and reactive
 * terminal signals.
 */
class LegalEntityDetailsUseCaseTest {

    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final Instant NOW = Instant.parse("2026-09-13T00:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 13);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.ofHours(-8));
    private static final RequestMetadata METADATA = new RequestMetadata(
            new TenantId(UUID.randomUUID()), "editor", UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());
    private static final PartyVersion VERSION = new PartyVersion(2);

    @Test
    void retrievalIsLazyAndPreservesHistoricalRepresentations() {
        Fixture fixture = new Fixture();
        LegalEntity original = fixture.current;
        Uni<LegalEntityResult> operation = new GetLegalEntityUseCase(fixture)
                .execute(new GetLegalEntityCommand(METADATA, PARTY_ID));
        assertTrue(fixture.events.isEmpty());
        LegalEntityResult result = item(operation);
        assertEquals(" Historical Legal ", result.legalName());
        assertEquals("Custom Label", result.displayName());
        assertEquals(VERSION, result.version());
        assertEquals(List.of("find"), fixture.events);
        assertSame(original, fixture.current);
    }

    @Test
    void absenceStopsEveryOperationBeforeCountryValidationOrWriting() {
        for (int operation = 0; operation < 3; operation++) {
            Fixture fixture = new Fixture();
            fixture.current = null;
            Uni<LegalEntityResult> result = switch (operation) {
                case 0 -> new GetLegalEntityUseCase(fixture).execute(new GetLegalEntityCommand(METADATA, PARTY_ID));
                case 1 -> fixture.replace().execute(replacement("GB"));
                default -> fixture.patch().execute(patchCommand(countryPatch("GB")));
            };
            assertEquals(new ApplicationFailure.LegalEntityNotFound(PARTY_ID, METADATA.tenantId()), failure(result));
            assertEquals(List.of("find"), fixture.events);
        }
    }

    @Test
    void replacementValidatesChangedCountryBeforeTheAtomicWrite() {
        Fixture fixture = new Fixture();
        LegalEntityResult result = item(fixture.replace().execute(replacement(" gb ")));
        assertEquals(List.of("find", "country:GB", "update"), fixture.events);
        assertEquals("UPDATED LEGAL", result.legalName());
        assertEquals("UPDATED LEGAL", result.displayName());
        assertEquals("BRAND", result.tradeName());
        assertEquals("GB", result.incorporationCountryCode());
        assertEquals(VERSION.next(), result.version());
        assertEquals(NOW, result.updatedAt());
        assertEquals("editor", result.updatedBy());
        assertNull(result.dissolvedOn());
    }

    @Test
    void partialUpdateRetainsOmittedFieldsAndNeedsNoUnchangedCountryLookup() {
        Fixture fixture = new Fixture();
        fixture.recognition = Uni.createFrom().failure(new IllegalStateException("Dependency must not be called"));
        LegalEntityPatch patch = new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
        LegalEntityResult result = item(fixture.patch().execute(patchCommand(patch)));
        assertNull(result.tradeName());
        assertEquals(" Historical Legal ", result.legalName());
        assertEquals(" Ltd ", result.legalFormCode());
        assertEquals("Custom Label", result.displayName());
        assertEquals(List.of("find", "update"), fixture.events);
    }

    @Test
    void equivalentCountrySkipsLookupForReplacementAndHelperComparison() {
        Fixture fixture = new Fixture();
        fixture.recognition = Uni.createFrom().failure(new IllegalStateException("Dependency unavailable"));
        item(fixture.replace().execute(replacement(" ec ")));
        assertEquals(List.of("find", "update"), fixture.events);
        fixture.events.clear();
        CountryValidation.validateChangedIncorporationCountry(fixture.country, METADATA, " ec ", "EC")
                .subscribe().withSubscriber(UniAssertSubscriber.create()).awaitItem(WAIT).assertCompleted();
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void staleReplacementVersionPrecedesInvalidDatesAndCountryFailure() {
        Fixture fixture = new Fixture();
        fixture.recognition = Uni.createFrom().failure(new IllegalStateException("Country unavailable"));
        ReplaceLegalEntityCommand command = new ReplaceLegalEntityCommand(METADATA, PARTY_ID,
                PartyVersion.initial(), "Legal", null, null, "GB", TODAY.plusDays(2), TODAY.plusDays(1));
        assertEquals(new ApplicationFailure.ExpectedVersionMismatch(PartyVersion.initial(), VERSION),
                failure(fixture.replace().execute(command)));
        assertEquals(List.of("find"), fixture.events);
    }

    @Test
    void stalePatchVersionPrecedesDomainValidationAndCountryFailure() {
        Fixture fixture = new Fixture();
        fixture.recognition = Uni.createFrom().failure(new IllegalStateException("Country unavailable"));
        LegalEntityPatch invalid = new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present("GB"),
                FieldUpdate.present(TODAY.plusDays(2)), FieldUpdate.present(TODAY.plusDays(1)));
        assertEquals(new ApplicationFailure.ExpectedVersionMismatch(PartyVersion.initial(), VERSION),
                failure(fixture.patch().execute(new PatchLegalEntityCommand(
                        METADATA, PARTY_ID, PartyVersion.initial(), invalid))));
        assertEquals(List.of("find"), fixture.events);
    }

    @Test
    void unrecognizedCountryHasTheIncorporationSpecificFailureForBothUpdates() {
        for (boolean replace : List.of(false, true)) {
            Fixture fixture = new Fixture();
            fixture.recognition = Uni.createFrom().item(false);
            assertEquals(new ApplicationFailure.UnrecognizedIncorporationCountry("GB"),
                    failure(updateCountry(fixture, replace)));
            assertEquals(List.of("find", "country:GB"), fixture.events);
            assertEquals(VERSION, fixture.current.version());
        }
    }

    @Test
    void unusableCountryResultIsNotMisclassifiedAsUnrecognized() {
        for (boolean replace : List.of(false, true)) {
            Fixture fixture = new Fixture();
            fixture.recognition = Uni.createFrom().nullItem();
            assertEquals(new ApplicationFailure.DependencyUnavailable("geographic-reference"),
                    failure(updateCountry(fixture, replace)));
            assertEquals(List.of("find", "country:GB"), fixture.events);
        }
    }

    @Test
    void dependencyAndPersistenceFailuresArePreservedWithoutRetries() {
        ApplicationException dependency = new ApplicationException(
                new ApplicationFailure.DependencyUnavailable("geographic-reference"));
        Fixture countryFailure = new Fixture();
        countryFailure.recognition = Uni.createFrom().failure(dependency);
        assertSame(dependency, throwable(countryFailure.replace().execute(replacement("GB"))));
        assertEquals(List.of("find", "country:GB"), countryFailure.events);

        ApplicationException mismatch = new ApplicationException(
                new ApplicationFailure.ExpectedVersionMismatch(VERSION, VERSION.next()));
        Fixture lostRace = new Fixture();
        lostRace.updateFailure = mismatch;
        assertSame(mismatch, throwable(lostRace.patch().execute(patchCommand(countryPatch("EC")))));
        assertEquals(List.of("find", "update"), lostRace.events);
    }

    @Test
    void semanticDateFailureStopsTheWriteAndRetainsItsDomainCause() {
        Fixture fixture = new Fixture();
        ReplaceLegalEntityCommand invalid = new ReplaceLegalEntityCommand(METADATA, PARTY_ID, VERSION,
                "Legal", null, null, "GB", TODAY.plusDays(1), null);
        assertEquals(new ApplicationFailure.InvalidBusinessState(DomainViolation.INCORPORATION_DATE_IN_FUTURE),
                failure(fixture.replace().execute(invalid)));
        assertEquals(List.of("find"), fixture.events);
        Fixture patchFixture = new Fixture();
        LegalEntityPatch patch = new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.present(LocalDate.of(2019, Month.JANUARY, 1)));
        assertEquals(new ApplicationFailure.InvalidBusinessState(DomainViolation.DISSOLUTION_BEFORE_INCORPORATION),
                failure(patchFixture.patch().execute(patchCommand(patch))));
        assertEquals(List.of("find"), patchFixture.events);
    }

    @Test
    void evaluationUsesUtcRatherThanTheClockZone() {
        Fixture replacement = new Fixture();
        LegalEntityResult result = item(replacement.replace().execute(new ReplaceLegalEntityCommand(
                METADATA, PARTY_ID, VERSION, "Legal", null, null, "EC", TODAY, TODAY)));
        assertEquals(TODAY, result.incorporatedOn());
        Fixture partial = new Fixture();
        LegalEntityPatch dates = new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.present(TODAY), FieldUpdate.present(TODAY));
        assertEquals(TODAY, item(partial.patch().execute(patchCommand(dates))).dissolvedOn());
    }

    @Test
    void cancellationDuringCountryValidationCannotTriggerTheWrite() {
        for (boolean replace : List.of(false, true)) {
            Fixture fixture = new Fixture();
            AtomicBoolean cancelled = new AtomicBoolean();
            fixture.recognition = Uni.createFrom().<Boolean>nothing()
                    .onCancellation().invoke(() -> cancelled.set(true));
            var subscriber = updateCountry(fixture, replace).subscribe().withSubscriber(UniAssertSubscriber.create());
            subscriber.cancel();
            assertTrue(cancelled.get());
            assertEquals(List.of("find", "country:GB"), fixture.events);
            assertEquals(VERSION, fixture.current.version());
        }
    }

    private static Uni<LegalEntityResult> updateCountry(Fixture fixture, boolean replace) {
        return replace ? fixture.replace().execute(replacement("GB"))
                : fixture.patch().execute(patchCommand(countryPatch("GB")));
    }

    private static ReplaceLegalEntityCommand replacement(String country) {
        return new ReplaceLegalEntityCommand(METADATA, PARTY_ID, VERSION,
                "Updated Legal", " Brand ", " sa ", country, null, null);
    }

    private static LegalEntityPatch countryPatch(String country) {
        return new LegalEntityPatch(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.present(country), FieldUpdate.absent(), FieldUpdate.absent());
    }

    private static PatchLegalEntityCommand patchCommand(LegalEntityPatch patch) {
        return new PatchLegalEntityCommand(METADATA, PARTY_ID, VERSION, patch);
    }

    private static LegalEntityResult item(Uni<LegalEntityResult> operation) {
        return operation.subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(WAIT).assertCompleted().getItem();
    }

    private static Throwable throwable(Uni<?> operation) {
        return operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(WAIT).getFailure();
    }

    private static ApplicationFailure failure(Uni<?> operation) {
        return assertInstanceOf(ApplicationException.class, throwable(operation)).failure();
    }

    /**
     * Records I/O ordering and models only the version acceptance guaranteed by the
     * repository port.
     */
    private static final class Fixture implements LegalEntityRepository {
        private final List<String> events = new ArrayList<>();
        private LegalEntity current = LegalEntity.restore(PARTY_ID, METADATA.tenantId(), "Custom Label",
                PartyRecordStatus.DRAFT, VERSION, AuditInfo.initial(NOW.minusSeconds(3600), "creator"),
                new LegalEntityDetails(" Historical Legal ", " Historical Brand ", " Ltd ", "EC",
                        LocalDate.of(2020, Month.JANUARY, 15), null));
        private Uni<Boolean> recognition = Uni.createFrom().item(true);
        private ApplicationException updateFailure;
        private final CountryReferencePort country = (metadata, code) -> {
            assertSame(METADATA, metadata);
            events.add("country:" + code);
            return recognition;
        };

        @Override
        public Uni<Optional<LegalEntity>> findByTenantAndId(TenantId tenantId, PartyId partyId) {
            assertEquals(METADATA.tenantId(), tenantId);
            assertEquals(PARTY_ID, partyId);
            events.add("find");
            return Uni.createFrom().item(Optional.ofNullable(current));
        }

        @Override
        public Uni<LegalEntity> update(LegalEntity candidate, PartyVersion expectedVersion) {
            events.add("update");
            assertEquals(VERSION, expectedVersion);
            assertEquals(VERSION, candidate.version());
            if (updateFailure != null) {
                return Uni.createFrom().failure(updateFailure);
            }
            current = LegalEntity.restore(candidate.partyId(), candidate.tenantId(), candidate.displayName(),
                    candidate.recordStatus(), expectedVersion.next(), candidate.auditInfo(), candidate.details());
            return Uni.createFrom().item(current);
        }

        private ReplaceLegalEntityUseCase replace() {
            return new ReplaceLegalEntityUseCase(this, country, CLOCK);
        }

        private PatchLegalEntityUseCase patch() {
            return new PatchLegalEntityUseCase(this, country, CLOCK);
        }
    }
}
