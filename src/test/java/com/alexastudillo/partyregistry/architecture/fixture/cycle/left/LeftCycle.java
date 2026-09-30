package com.alexastudillo.partyregistry.architecture.fixture.cycle.left;

import com.alexastudillo.partyregistry.architecture.fixture.cycle.right.RightCycle;

/**
 * Provides the left side of a forbidden package cycle.
 */
public final class LeftCycle {

    private RightCycle right;

    public RightCycle right() {
        return right;
    }
}
