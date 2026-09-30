package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks inclusive validity, presence-aware updates, and Party-scoped overlap
 * rules.
 */
class NationalityPeriodPolicyTest {

    private static final LocalDate START = LocalDate.of(2026, Month.JANUARY, 1);
    private static final LocalDate END = LocalDate.of(2026, Month.JANUARY, 31);
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());

    @Test
    void openAndSingleDayIntervalsAreInclusive() {
        NationalityPeriod open = new NationalityPeriod(null, null);
        NationalityPeriod day = new NationalityPeriod(END, END);
        assertTrue(open.overlaps(day));
        assertTrue(day.contains(END));
        assertFalse(day.contains(END.plusDays(1)));
        assertTrue(new NationalityPeriod(START, END).overlaps(day));
        assertFalse(new NationalityPeriod(START, END).overlaps(
                new NationalityPeriod(END.plusDays(1), null)));
        assertTrue(new NationalityPeriod(null, END).overlaps(new NationalityPeriod(END, null)));
    }

    @Test
    void rejectsInvertedIntervalsIncludingAfterPatchMerge() {
        DomainValidationException invalid = assertThrows(DomainValidationException.class,
                () -> new NationalityPeriod(END, START));
        assertEquals(DomainViolation.NATIONALITY_VALIDITY_DATE_ORDER, invalid.violation());
        NationalityPeriod current = new NationalityPeriod(START, END);
        assertEquals(new NationalityPeriod(null, END),
                current.merge(FieldUpdate.present(null), FieldUpdate.absent()));
        assertEquals(new NationalityPeriod(START, null),
                current.merge(FieldUpdate.absent(), FieldUpdate.present(null)));
        assertEquals(current, current.merge(FieldUpdate.absent(), FieldUpdate.absent()));
        FieldUpdate<LocalDate> invalidFrom = FieldUpdate.present(END.plusDays(1));
        FieldUpdate<LocalDate> absentUntil = FieldUpdate.absent();
        assertEquals(DomainViolation.NATIONALITY_VALIDITY_DATE_ORDER,
                assertThrows(DomainValidationException.class, () -> current.merge(invalidFrom, absentUntil))
                        .violation());
    }

    @Test
    void protectsSameCountryAndPrimaryIntervalsWithoutChangingOtherParties() {
        PartyNationality first = nationality(PARTY, "EC", true, new NationalityPeriod(START, END));
        PartyNationality sameCountry = nationality(PARTY, "EC", false, new NationalityPeriod(END, null));
        PartyNationality otherPrimary = nationality(PARTY, "CO", true, new NationalityPeriod(END, null));
        List<PartyNationality> existing = List.of(first);
        assertEquals(DomainViolation.NATIONALITY_VALIDITY_CONFLICT,
                assertThrows(DomainValidationException.class,
                        () -> NationalityPeriodPolicy.validate(sameCountry, existing))
                        .violation());
        assertEquals(DomainViolation.PRIMARY_NATIONALITY_CONFLICT,
                assertThrows(DomainValidationException.class,
                        () -> NationalityPeriodPolicy.validate(otherPrimary, existing))
                        .violation());
        NationalityPeriodPolicy.validate(nationality(PARTY, "CO", true,
                new NationalityPeriod(END.plusDays(1), null)), existing);
        NationalityPeriodPolicy.validate(sameCountry, List.of(nationality(
                new PartyId(UUID.randomUUID()), "EC", true, new NationalityPeriod(null, null))));
    }

    @Test
    void eligibilityDistinguishesCurrentFutureAndEndedTargets() {
        PartyNationality current = nationality(PARTY, "EC", false, new NationalityPeriod(START, END));
        NationalityPeriodPolicy.requireEffective(current, START);
        NationalityPeriodPolicy.requireEffective(current, END);
        for (LocalDate date : List.of(START.minusDays(1), END.plusDays(1))) {
            assertEquals(DomainViolation.NATIONALITY_NOT_EFFECTIVE,
                    assertThrows(DomainValidationException.class,
                            () -> NationalityPeriodPolicy.requireEffective(current, date))
                            .violation());
        }
    }

    private static PartyNationality nationality(PartyId party, String code, boolean primary, NationalityPeriod period) {
        return new PartyNationality(new NationalityId(UUID.randomUUID()), party, code, primary, period,
                AuditInfo.initial(Instant.parse("2026-01-01T00:00:00Z"), "operator"));
    }
}
