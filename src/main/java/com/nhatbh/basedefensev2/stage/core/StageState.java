package com.nhatbh.basedefensev2.stage.core;

/** Outer lifecycle states for an arena stage. */
public enum StageState {
    /** Brief countdown before combat. Players are teleported into the arena. */
    WARMUP,
    /** Main combat phase. Delegates to WaveState for inner loop. */
    ACTIVE,
    /** Victory phase. Rewards are granted on tick 1. */
    SCAVENGE,
    /** Cleanup complete. Arena is wiped, players are teleported out. */
    ENDED
}
