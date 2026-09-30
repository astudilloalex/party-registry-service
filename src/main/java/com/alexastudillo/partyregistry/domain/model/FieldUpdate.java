package com.alexastudillo.partyregistry.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * Distinguishes an omitted patch field from a present field whose value may be
 * null.
 *
 * @param <T> field value type
 */
public final class FieldUpdate<T> {

    private final boolean present;
    private final @Nullable T value;

    private FieldUpdate(boolean present, @Nullable T value) {
        this.present = present;
        this.value = value;
    }

    public static <T> FieldUpdate<T> absent() {
        return new FieldUpdate<>(false, null);
    }

    public static <T> FieldUpdate<T> present(@Nullable T value) {
        return new FieldUpdate<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    public @Nullable T value() {
        return value;
    }
}
