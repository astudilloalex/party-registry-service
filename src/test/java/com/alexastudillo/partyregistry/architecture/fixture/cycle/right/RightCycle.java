package com.alexastudillo.partyregistry.architecture.fixture.cycle.right;

import com.alexastudillo.partyregistry.architecture.fixture.cycle.left.LeftCycle;

/**
 * Provides the right side of a forbidden package cycle.
 */
public final class RightCycle {

    private LeftCycle left;

    public LeftCycle left() {
        return left;
    }
}
