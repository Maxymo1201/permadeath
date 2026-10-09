package com.serthekiller.permadeath.core;

/**
 * One player of the D60 final challenge (persisted). Values are updated while the player is online; the last
 * known values are used for players who are offline when the challenge ends.
 */
public final class FinalParticipant {
    public enum Result {
        PENDING, VICTORY, DEFEAT
    }

    public String name;
    /** Died permanently (Permadeath) during the final challenge. */
    public boolean eliminated;
    /** Held a Life Orb at some point before the countdown ran out. */
    public boolean lifeOrbBeforeDeadline;
    /** Held a Life Orb the last time the player was seen online. */
    public boolean holdingLifeOrb;
    /** Was an eligible (alive, survival) player the last time the player was seen online. */
    public boolean survivedLastSeen = true;
    public Result result = Result.PENDING;

    public FinalParticipant() {
    }

    public FinalParticipant(String name) {
        this.name = name;
    }
}
