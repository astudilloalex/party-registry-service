package com.alexastudillo.partyregistry.application;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long total) {
    public PageResult {
        items = List.copyOf(items);
        if (page < 0 || size < 1 || total < 0) {
            throw new IllegalArgumentException("Invalid page result");
        }
    }
}
