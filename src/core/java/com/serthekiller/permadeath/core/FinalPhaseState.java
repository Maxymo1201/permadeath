package com.serthekiller.permadeath.core;

/** State of the D60 final challenge (persisted). */
public enum FinalPhaseState {
    /** D60 not reached yet, or reached while no eligible survivor was online. */
    NOT_STARTED,
    /** The final challenge is running (Life Orb countdown, periodic Withers, final timer). */
    ACTIVE,
    /** The final timer ran out and at least one participant won. */
    COMPLETED,
    /** Every participant was eliminated, or nobody met the objectives when the timer ran out. */
    FAILED;

    public boolean finished() {
        return this == COMPLETED || this == FAILED;
    }
}
