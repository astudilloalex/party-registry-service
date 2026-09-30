package com.alexastudillo.partyregistry.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies presence semantics for every legal-detail patch property. */
class LegalEntityPatchTest {

    private static final LocalDate DATE = LocalDate.of(2020, Month.JANUARY, 15);

    @Test
    void emptyPatchOmitsEveryProperty() {
        LegalEntityPatch patch = LegalEntityPatch.empty();
        assertTrue(patch.isEmpty());
        for (FieldUpdate<?> field : fields(patch)) {
            assertFalse(field.isPresent());
            assertNull(field.value());
        }
    }

    @Test
    void everyPropertyDistinguishesOmittedNullAndValue() {
        for (int selected = 0; selected < 6; selected++) {
            for (boolean nullValue : List.of(false, true)) {
                LegalEntityPatch patch = patchWith(selected, nullValue);
                assertFalse(patch.isEmpty());
                List<FieldUpdate<?>> fields = fields(patch);
                for (int index = 0; index < fields.size(); index++) {
                    FieldUpdate<?> field = fields.get(index);
                    assertEquals(index == selected, field.isPresent());
                    Object expected = index != selected || nullValue ? null : index < 4 ? "value" : DATE;
                    assertEquals(expected, field.value());
                }
            }
        }
    }

    @Test
    void rejectsMissingPresenceContainers() {
        FieldUpdate<String> text = FieldUpdate.absent();
        FieldUpdate<LocalDate> date = FieldUpdate.absent();
        assertThrows(NullPointerException.class, () -> new LegalEntityPatch(null, text, text, text, date, date));
        assertThrows(NullPointerException.class, () -> new LegalEntityPatch(text, null, text, text, date, date));
        assertThrows(NullPointerException.class, () -> new LegalEntityPatch(text, text, null, text, date, date));
        assertThrows(NullPointerException.class, () -> new LegalEntityPatch(text, text, text, null, date, date));
        assertThrows(NullPointerException.class, () -> new LegalEntityPatch(text, text, text, text, null, date));
        assertThrows(NullPointerException.class, () -> new LegalEntityPatch(text, text, text, text, date, null));
    }

    private static LegalEntityPatch patchWith(int index, boolean nullValue) {
        return new LegalEntityPatch(
                index == 0 ? FieldUpdate.present(nullValue ? null : "value") : FieldUpdate.absent(),
                index == 1 ? FieldUpdate.present(nullValue ? null : "value") : FieldUpdate.absent(),
                index == 2 ? FieldUpdate.present(nullValue ? null : "value") : FieldUpdate.absent(),
                index == 3 ? FieldUpdate.present(nullValue ? null : "value") : FieldUpdate.absent(),
                index == 4 ? FieldUpdate.present(nullValue ? null : DATE) : FieldUpdate.absent(),
                index == 5 ? FieldUpdate.present(nullValue ? null : DATE) : FieldUpdate.absent());
    }

    private static List<FieldUpdate<?>> fields(LegalEntityPatch patch) {
        return List.of(patch.legalName(), patch.tradeName(), patch.legalFormCode(),
                patch.incorporationCountryCode(), patch.incorporatedOn(), patch.dissolvedOn());
    }
}
